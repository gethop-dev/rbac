# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0.alpha-3] - 2021-02-24
### Changed
- Upgraded `honeysql` dependency, to fix a potential SQL injection security bug.

## [0.1.0.alpha-2] - 2020-09-22
### Fixed
- `has-permission` sometimes returned `nil` instead of `false` when the user didn't have the requested permission on the resource of the given context type. Now it always returns `false`.

## [0.1.0.alpha-1] - 2020-08-13
- First public release. This is alpha quality software.

[Unreleased]: https://github.com/magnetcoop/rbac/compare/v0.1.0.alpha-3...HEAD
[0.1.0.alpha-3]: https://github.com/magnetcoop/rbac/compare/v0.1.0.alpha-2...v0.1.0.alpha-3
[0.1.0.alpha-2]: https://github.com/magnetcoop/rbac/compare/v0.1.0.alpha-1...v0.1.0.alpha-2
[0.1.0.alpha-1]: https://github.com/magnetcoop/rbac/releases/tag/v0.1.0.alpha-1
