# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0-alpha-7] - 2022-11-29
### Added
- Support string and integer-based id types for User and Resource ids.

### Fixed
- Avoid code's dependency on PostgreSQL, so any other SQL-compliant DB can be used with this library.

## [0.1.0-alpha-6] - 2021-06-23
### Fixed
- Fix `get-role-assignments-by-user` methods return structure
  'ids'. The 'ids' were being returned as 'string's instead of
  'uuid's.

## [0.1.0-alpha-5] - 2021-06-23
### Fixed
- Fix `get-role-assignments-by-user` method return structure. The shape of the structure
  in the previous version was not following the structure defined in the specs.

## [0.1.0-alpha-4] - 2021-06-23
### Added
- `get-role-assignments-by-user` method to get list of role+contexts assigned
  to a specific user. It additionaly returns the `role-name`,
  `role-description` and `context-type-name`.
### Fixed
- Limitation of rbac_role_assigment primary key (issue [#1](https://github.com/gethop-dev/rbac/issues/1))

## [0.1.0.alpha-3] - 2021-02-24
### Changed
- Upgraded `honeysql` dependency, to fix a potential SQL injection security bug.

## [0.1.0.alpha-2] - 2020-09-22
### Fixed
- `has-permission` sometimes returned `nil` instead of `false` when the user didn't have the requested permission on the resource of the given context type. Now it always returns `false`.

## [0.1.0.alpha-1] - 2020-08-13
- First public release. This is alpha quality software.

[Unreleased]: https://github.com/gethop-dev/rbac/compare/v0.1.0.alpha-7...HEAD
[0.1.0.alpha-7]: https://github.com/gethop-dev/rbac/releases/tag/v0.1.0.alpha-7
[0.1.0.alpha-6]: https://github.com/gethop-dev/rbac/releases/tag/v0.1.0.alpha-6
[0.1.0.alpha-5]: https://github.com/gethop-dev/rbac/releases/tag/v0.1.0.alpha-5
[0.1.0.alpha-4]: https://github.com/gethop-dev/rbac/releases/tag/v0.1.0.alpha-4
[0.1.0.alpha-3]: https://github.com/gethop-dev/rbac/releases/tag/v0.1.0.alpha-3
[0.1.0.alpha-2]: https://github.com/gethop-dev/rbac/releases/tag/v0.1.0.alpha-2
[0.1.0.alpha-1]: https://github.com/gethop-dev/rbac/releases/tag/v0.1.0.alpha-1
