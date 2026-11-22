# Firebase setup (do these once)

SHA fingerprints of the signing key in `app/zameenhisab.jks` (the same key signs debug and release builds):

- SHA-1:   E0:5B:86:DE:E9:5C:5B:A0:48:79:B7:5F:69:EB:C4:4C:E0:91:06:F7
- SHA-256: AF:CE:65:8C:9A:4B:FA:AF:28:BA:B1:B9:47:1E:1A:26:30:C2:2D:5E:16:00:DA:A8:BB:DF:1F:A7:09:96:02:01

1. Project settings -> Your apps -> Android app `com.zameenhisab.app` -> **Add fingerprint**: add both SHA-1 and SHA-256.
2. On the same page, **download google-services.json again** and replace `app/google-services.json`.
   (The current file has no Android OAuth client, so Google sign-in cannot work until this is done.)
3. Build -> Authentication -> Sign-in method: enable **Google**, **Phone** and **Email/Password**.
   (Do not add a sign-up screen for email; email accounts are only created by you in the Authentication > Users tab.)
4. Build -> Realtime Database -> Rules: paste `database.rules.json` and publish.
5. Phone sign-in: enable the **Play Integrity API** for the project in Google Cloud console. For testing without SMS,
   add test numbers under Authentication > Sign-in method > Phone.
   Real SMS to Pakistan numbers may require the Blaze plan and an allowed region (Authentication > Settings > SMS region policy).
6. The old APKs were signed with a different (random) key, so uninstall them before installing the new build.

Security note: `app/zameenhisab.jks` and its passwords are in the repo. Fine for testing; before a Play Store release
create a private keystore and move the passwords to GitHub secrets.

Data layout: `users/<uid>/khata`, `users/<uid>/hist`, `users/<uid>/s` (each a JSON string). Guests never write anything.
