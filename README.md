# Modrintherator CLI

A utility for Modrinth file moderators to get the easy and repetitive stuff out of the way.

Copyright 2022 Emmaffle, [zlib license](LICENSE).

## Usage

`java -jar modrintherator-cli.jar -p AABBCCDD`

### Options

| Short option | Long option   | Has argument? | Default           | Nullable?     | Description                |
|--------------|---------------|---------------|-------------------|---------------|----------------------------|
| `-t`         | `--token`     | Yes           | `$MODRINTH_TOKEN` | No            | Modrinth token to use      |
| `-g`         | `--ghToken`   | Yes           | `$GITHUB_OAUTH`   | Yes           | GitHub OAuth token to use  |
| `-p`         | `--projectId` | Yes           | None              | No            | ID of the project to check |
| `-s`         | `--staging`   | No            | False             | False if null | Whether to use staging API |