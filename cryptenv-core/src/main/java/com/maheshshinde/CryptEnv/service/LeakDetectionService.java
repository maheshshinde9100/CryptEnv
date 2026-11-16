package com.maheshshinde.CryptEnv.service;

import com.maheshshinde.CryptEnv.exception.ResourceNotFoundException;
import com.maheshshinde.CryptEnv.model.LeakFinding;
import com.maheshshinde.CryptEnv.model.User;
import com.maheshshinde.CryptEnv.repository.LeakFindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Regex and entropy scanner that stores only metadata and SHA-256 fingerprints. */
@Service @RequiredArgsConstructor
public class LeakDetectionService {
    private record Rule(String name, Pattern pattern) {}
    private static final List<Rule> RULES = List.of(
            new Rule("AWS access key", Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b")),
            new Rule("GitHub token", Pattern.compile("\\bgh[pousr]_[A-Za-z0-9_]{30,}\\b")),
            new Rule("Private key", Pattern.compile("-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----")),
            new Rule("Credential assignment", Pattern.compile("(?:password|secret|api[_-]?key|token)\\s*[:=]\\s*['\\\"][^'\\\"]{8,}['\\\"]", Pattern.CASE_INSENSITIVE)));
    private final LeakFindingRepository findings;
    private final SecurityService security;
    private final AuditLogService audit;
    @Transactional public List<Map<String,Object>> scan(String source, String content, Long workspaceId, Long environmentId) {
        if (content == null) throw new IllegalArgumentException("content is required");
        User user = security.getCurrentUser(); List<Map<String,Object>> output = new ArrayList<>(); String[] lines = content.split("\\R", -1);
        for (int line = 0; line < lines.length; line++) {
            if (lines[line].matches(".*(?:cryptenv:ignore|gitleaks:allow).*")) continue;
            for (Rule rule : RULES) { Matcher matcher = rule.pattern.matcher(lines[line]); while (matcher.find()) output.add(save(user, source, line + 1, rule.name, matcher.group(), workspaceId, environmentId)); }
            Matcher token = Pattern.compile("\\b[A-Za-z0-9+/_=-]{24,}\\b").matcher(lines[line]); while (token.find()) if (entropy(token.group()) >= 4.5) output.add(save(user, source, line + 1, "High-entropy value", token.group(), workspaceId, environmentId));
        }
        audit.logEvent(user, "LEAK_SCAN", "LEAK_SCAN", source, true, null, "findings=" + output.size(), workspaceId); return output;
    }
    @Transactional(readOnly = true) public List<Map<String,Object>> list() { return findings.findByUserIdOrderByCreatedAtDesc(security.getCurrentUser().getId()).stream().map(this::view).toList(); }
    @Transactional public Map<String,Object> update(Long id, String status) { LeakFinding item = findings.findByIdAndUserId(id, security.getCurrentUser().getId()).orElseThrow(() -> new ResourceNotFoundException("Leak finding not found")); try { item.setStatus(LeakFinding.Status.valueOf(status)); } catch (Exception e) { throw new IllegalArgumentException("Invalid finding status"); } return view(findings.save(item)); }
    private Map<String,Object> save(User user,String source,int line,String rule,String value,Long workspaceId,Long environmentId) { return view(findings.save(LeakFinding.builder().user(user).source(source == null ? "unknown" : source).lineNumber(line).ruleName(rule).valueFingerprint(hash(value)).workspaceId(workspaceId).environmentId(environmentId).build())); }
    private Map<String,Object> view(LeakFinding item) { Map<String,Object> result=new LinkedHashMap<>(); result.put("id",item.getId());result.put("source",item.getSource());result.put("lineNumber",item.getLineNumber());result.put("ruleName",item.getRuleName());result.put("fingerprint",item.getValueFingerprint().substring(0,12));result.put("status",item.getStatus().name());result.put("workspaceId",item.getWorkspaceId());result.put("environmentId",item.getEnvironmentId());result.put("createdAt",item.getCreatedAt());return result; }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private double entropy(String value) { Map<Character,Integer> counts=new HashMap<>(); for(char c:value.toCharArray()) counts.merge(c,1,Integer::sum); double entropy=0;for(int count:counts.values()){double p=(double)count/value.length();entropy-=p*(Math.log(p)/Math.log(2));}return entropy; }
}
