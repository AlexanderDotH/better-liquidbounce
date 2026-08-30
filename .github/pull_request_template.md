# Pull request

## Scope

Describe the single feature or neutral shared responsibility changed by this
pull request.

## Preserved contracts

List the characterized module, setting/config, event, packet, cleanup, Mixin,
Script API, REST/WebSocket, or UI contracts that remain unchanged.

## Verification checklist

- [ ] I read `build/reports/source-hygiene/source-quality.md` before editing
      reported files.
- [ ] Characterization tests were added before production behavior moved.
- [ ] Mechanical moves kept methods unchanged; renaming or simplification is
      separate.
- [ ] The change introduces no feature work or intended behavior change.
- [ ] Dependencies use an allowed contract, stable module facade, explicit
      bridge, or neutral shared core.
- [ ] No structural suppression, allowlist, forbidden dependency, or package
      cycle was added.
- [ ] The ratchet baseline was not raised; every touched legacy violation
      decreased or disappeared.
- [ ] New recurring anti-patterns include a rule ID, gate test, actionable
      feedback, and Coding Standards entry.
- [ ] `qualityGate` passed with Java 25, Node 24.18.1, and npm
      12.0.2.
- [ ] `build verifyReleaseArtifact` validated the remapped release JAR, not a
      dev or sources JAR.
- [ ] No artifact was deployed and no gameplay claim is based only on automated
      verification.

## Evidence

Link the CI run and note relevant Markdown, JSON, SARIF, Detekt, test, and
release-artifact results.
