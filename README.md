# Modrintherator CLI

A utility for Modrinth file moderators to get the easy and repetitive stuff out of the way.

Copyright 2022 Emmaffle, [zlib license](LICENSE).

## Usage

Examples of possible valid usages:
`java -jar modrintherator-cli.jar AABBCCDD`
`java -jar modrintherator-cli.jar https://modrinth.com/mod/AABBCCDD`

`java -jar modrintherator-cli.jar EEFFGGHH`
`java -jar modrintherator-cli.jar EEFFGGHH --staging`
`java -jar modrintherator-cli.jar https://staging.modrinth.com/mod/EEFFGGHH`

Set the `$MODRINTH_TOKEN` environment variable to your Modrinth token, and the `$GITHUB_OAUTH` environment variable to
your GitHub OAuth token (can be obtained from `gh auth status -t` with the GitHub CLI, and is not required).
