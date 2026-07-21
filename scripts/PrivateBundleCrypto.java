import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.*;

public final class PrivateBundleCrypto {
    private static final byte[] MAGIC = "ISDENC01".getBytes(StandardCharsets.US_ASCII);
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;

    private PrivateBundleCrypto() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3) usage();
        char[] password = readPassword();
        try {
            switch (args[0]) {
                case "encrypt" -> encrypt(Path.of(args[1]), Arrays.copyOfRange(args, 2, args.length), password);
                case "decrypt" -> decrypt(Path.of(args[1]), Path.of(args[2]), password);
                default -> usage();
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static void encrypt(Path output, String[] inputs, char[] password) throws Exception {
        byte[] plain = zip(inputs);
        byte[] salt = randomBytes(16);
        byte[] iv = randomBytes(12);
        SecretKey key = derive(password, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(MAGIC);
        byte[] encrypted = cipher.doFinal(plain);

        Path absolute = output.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        try (DataOutputStream stream = new DataOutputStream(Files.newOutputStream(temporary))) {
            stream.write(MAGIC);
            stream.writeInt(ITERATIONS);
            stream.writeByte(salt.length);
            stream.writeByte(iv.length);
            stream.write(salt);
            stream.write(iv);
            stream.write(encrypted);
        }
        Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        Arrays.fill(plain, (byte) 0);
        System.out.println("Encrypted bundle created: " + absolute);
    }

    private static void decrypt(Path input, Path outputDirectory, char[] password) throws Exception {
        byte[] encrypted;
        byte[] salt;
        byte[] iv;
        try (DataInputStream stream = new DataInputStream(Files.newInputStream(input))) {
            byte[] magic = stream.readNBytes(MAGIC.length);
            if (!MessageDigestSupport.constantTimeEquals(MAGIC, magic)) throw new IOException("Invalid bundle header");
            int iterations = stream.readInt();
            if (iterations != ITERATIONS) throw new IOException("Unsupported key-derivation settings");
            int saltLength = stream.readUnsignedByte();
            int ivLength = stream.readUnsignedByte();
            if (saltLength != 16 || ivLength != 12) throw new IOException("Invalid bundle parameters");
            salt = stream.readNBytes(saltLength);
            iv = stream.readNBytes(ivLength);
            encrypted = stream.readAllBytes();
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, derive(password, salt), new GCMParameterSpec(128, iv));
        cipher.updateAAD(MAGIC);
        byte[] plain = cipher.doFinal(encrypted);
        unzip(plain, outputDirectory);
        Arrays.fill(plain, (byte) 0);
        System.out.println("Bundle restored to: " + outputDirectory.toAbsolutePath().normalize());
    }

    private static byte[] zip(String[] specs) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Set<String> names = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (String spec : specs) {
                int separator = spec.indexOf('=');
                if (separator < 1) throw new IllegalArgumentException("Input must use archive/path=source/path: " + spec);
                String name = normalizeEntry(spec.substring(0, separator));
                Path source = Path.of(spec.substring(separator + 1)).toAbsolutePath().normalize();
                if (!Files.isRegularFile(source)) throw new FileNotFoundException(source.toString());
                if (!names.add(name)) throw new IllegalArgumentException("Duplicate archive path: " + name);
                ZipEntry entry = new ZipEntry(name);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                Files.copy(source, zip);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void unzip(byte[] bytes, Path outputDirectory) throws IOException {
        Path root = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path destination = root.resolve(normalizeEntry(entry.getName())).normalize();
                if (!destination.startsWith(root)) throw new IOException("Unsafe archive entry");
                Files.createDirectories(destination.getParent());
                Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String normalizeEntry(String value) {
        String normalized = value.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..")) {
            throw new IllegalArgumentException("Unsafe archive path: " + value);
        }
        return normalized;
    }

    private static SecretKey derive(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        try {
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            try { return new SecretKeySpec(encoded, "AES"); }
            finally { Arrays.fill(encoded, (byte) 0); }
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        new SecureRandom().nextBytes(value);
        return value;
    }

    private static char[] readPassword() {
        Console console = System.console();
        if (console != null) {
            char[] value = console.readPassword("Bundle password: ");
            if (value == null || value.length < 16) throw new IllegalArgumentException("Password must contain at least 16 characters");
            return value;
        }
        String environment = System.getenv("PRIVATE_BUNDLE_PASSWORD");
        if (environment == null || environment.length() < 16) {
            throw new IllegalStateException("Use an interactive console or set PRIVATE_BUNDLE_PASSWORD with at least 16 characters");
        }
        return environment.toCharArray();
    }

    private static void usage() {
        throw new IllegalArgumentException(
                "Usage: java PrivateBundleCrypto.java encrypt <output> <archive/path=source/path>... "
                        + "or decrypt <input> <output-directory>");
    }

    private static final class MessageDigestSupport {
        static boolean constantTimeEquals(byte[] left, byte[] right) {
            return java.security.MessageDigest.isEqual(left, right);
        }
    }
}
