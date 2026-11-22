# Signing key inventory

This file records where signing identities are referenced. It must not contain private key bytes, keystore files, passwords, recovery codes, or unencrypted backups. Keep signing material outside this repository.

## Local Android debug signing identity

- Keystore location: `%USERPROFILE%\.android\debug.keystore` (local workstation only)
- Purpose: Gradle development/debug APK signing
- Public certificate SHA-256: `ece13bcf2e01d1d4848bae6513e72fc4ab16bde427ce0b3bf4f4140601bbd162`
- The keystore file was present on the build workstation when this inventory was written. Only its public certificate fingerprint is recorded here.
- This is a development signing identity, not a release key.

Store a backup of the keystore only in an encrypted password manager or encrypted offline vault. Keep the keystore password and key password in the same protected vault, not in this repository or a Markdown file.

## Release signing keys

No release signing key files were found in the app project checkouts during this inventory. If a release key is created, keep it outside the checkout, use an encrypted backup, and add only its label, protected-vault reference, intended use, and public certificate fingerprint here.

## Repository safeguards

Private key extensions such as `.pk8`, `.pem`, `.p12`, `.pfx`, `.jks`, `.keystore`, and `.key` are ignored by Git. Check `git status` before every commit and never force-add a private signing file.
