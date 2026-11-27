# Daily Shop platform parity

Apply feature additions, behavior changes, bug fixes, and removals to the Daily Shop management app on Android, iPhone, and desktop (Windows, macOS, and Linux). Check the normal entry point on each platform, including any separate desktop screens. Reuse shared code where it fits. Keep platform-specific controls and integrations consistent with the same user-facing behavior.

Verify affected flows on all three platforms. Record build, test, and release status per platform. State where verification is still pending before reporting a change as complete.

## Daily Shop merge and deployment

The project owner has given standing authorization to complete routine delivery for requested changes. Open or update the pull request, monitor the applicable checks, fix failures within scope, and squash-merge to `main` once the checks pass, without waiting for another confirmation. Follow the repository's release and deployment workflows for the affected app or service, including any staging steps, and verify the resulting build, deployment, or published version.

Use [the stable release runbook](docs/RELEASE-STABLE-fa.md) and the relevant service instructions for production. Before promoting a release, verify the documented prerequisites, including the tested commit and staged rollout where required. If a required check, access, credential, or rollout prerequisite blocks delivery, report the exact blocker and the current release status per platform.
