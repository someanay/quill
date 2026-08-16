# Quill Windows installer

`Publish-Quill.ps1` creates a self-contained x64 Windows installer at
`windows/artifacts/Quill-win-x64-Setup.exe` using the built-in IExpress tool. The installer:

- installs to `%LOCALAPPDATA%\Programs\Quill` without requiring administrator access;
- creates a Start Menu shortcut named **Quill**;
- registers an entry in Windows Apps & features for uninstall;
- launches Quill after installation, where the existing first-run setup wizard guides provider,
  key, privacy, and shortcut configuration.

For a local release build:

```powershell
powershell -ExecutionPolicy Bypass -File .\windows\installer\Publish-Quill.ps1
```

The output is self-contained, so end users do not need to install the .NET runtime separately.
Production distribution should replace the unsigned local installer with a code-signed release.
