# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Fixed
- `has-permission` sometimes returned `nil` instead of `false` when the user didn't have the requested permission on the resource of the given context type. Now it always returns `false`.

## [0.1.0.alpha-1] - 2020-08-13
- First public release. This is alpha quality software.

[Unreleased]: https://github.com/magnetcoop/rbac/compare/v0.1.0.alpha-1...HEAD
[0.1.0.alpha-1]: https://github.com/magnetcoop/rbac/releases/tag/v0.1.0.alpha-1
