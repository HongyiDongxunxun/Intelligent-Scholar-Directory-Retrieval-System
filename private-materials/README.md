# Encrypted Private Materials

`original-build-materials.isdenc` is an AES-256-GCM encrypted archive of the private source project's database schema, runtime configurations, and Maven build descriptor. It is not needed to build or run the public reconstruction.

Encryption uses PBKDF2-HMAC-SHA-256 with 210,000 iterations, a random 128-bit salt, a random 96-bit nonce, and a 128-bit GCM authentication tag. The password is not stored in this repository.

Use `scripts/protect_private_materials.ps1` to create or restore the archive. Restored plaintext is ignored by Git. Do not place the password in command-line arguments, scripts, commits, issues, CI variables visible to forks, or documentation.

The public `backend/pom.xml` and `backend/src/main/resources/application.yml` are sanitized reproducibility files containing no database connection, model key, user credential, host, or private repository coordinate.
