# Contributing to PUnit

Thank you for your interest in contributing to PUnit. This document
describes how to submit contributions and the legal terms that apply.

## License

PUnit is licensed under the [Apache License, Version 2.0](LICENSE).
All contributions are accepted under the same license.

## Developer Certificate of Origin

All contributions are subject to the
[Developer Certificate of Origin (DCO)](https://developercertificate.org/).
The DCO text is available verbatim in the [dco.txt](dco.txt) file in the
root of this repository.

By signing off your commits, you certify that you wrote the contribution
or otherwise have the right to submit it under the project's license. No
separate contributor agreement is required.

### Signing your commits

Add a `Signed-off-by` line to every commit message:

```
Signed-off-by: Your Name <your.email@example.com>
```

The easiest way is to use the `-s` (or `--signoff`) flag with `git commit`:

```
git commit -s -m "Your commit message"
```

The name and email must match the values configured in your git
identity (`git config user.name` and `git config user.email`).

To sign off a series of existing commits before pushing, use:

```
git rebase --signoff <base-branch>
```

Unsigned commits will be blocked by automated checks on pull requests.

## Reporting issues

Please use [GitHub Issues](https://github.com/mavai-org/punit/issues) for
bug reports and feature requests. Include a minimal reproducer where
possible.

## Pull requests

- Fork the repository and create a topic branch from `main`.
- Keep changes focused; one logical change per pull request.
- Ensure all commits are signed off (see above).
- Run the test suite locally before opening the pull request.
- Reference any related issue in the pull request description.
