# CryptEnv Java SDK

Runtime secret consumption SDK for Java applications.

Install CryptEnv secrets into your app at runtime — authenticate with CryptEnv, fetch encrypted secrets, decrypt locally with your master key, and use plaintext values in your code. No HTTP, JWT, or crypto code needed in your application.

## Install

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.maheshshinde</groupId>
    <artifactId>cryptenv-sdk</artifactId>
    <version>1.1.0</version>
</dependency>
```

## Configuration

Add CryptEnv credentials to your application's environment variables.

```env
CRYPTENV_API_URL=https://cryptenv-backend.onrender.com
CRYPTENV_EMAIL=you@example.com
CRYPTENV_PASSWORD=your-password
CRYPTENV_WORKSPACE_ID=1
CRYPTENV_MASTER_KEY=your-workspace-encryption-key
```

## Usage

```java
import com.maheshshinde.cryptenv.sdk.CryptEnvClient;

public class Main {
    public static void main(String[] args) throws Exception {
        // Automatically picks up credentials from environment variables
        CryptEnvClient cryptenv = new CryptEnvClient();
        
        // Authenticates and fetches encrypted secrets payload
        cryptenv.init();
        
        // Decrypts on-the-fly locally using your master key
        String databaseUrl = cryptenv.get("DATABASE_URL");
        System.out.println("Decrypted DB URL: " + databaseUrl);
        
        // List all available keys
        System.out.println("Available keys: " + cryptenv.listKeys());
    }
}
```

## Security

- The `CRYPTENV_MASTER_KEY` stays local and is never sent to the backend.
- Local AES-256-GCM decryption is used to decrypt your secrets in memory.

## License

MIT
