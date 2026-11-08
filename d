[33mcommit 99d9eef1263ad6e4020fac327298fb55b863d73b[m[33m ([m[1;36mHEAD[m[33m -> [m[1;32mmain[m[33m, [m[1;31morigin/main[m[33m, [m[1;31morigin/HEAD[m[33m)[m
Author: pedroraiher <242241641+pedroraiher@users.noreply.github.com>
Date:   Sun Nov 8 18:32:55 2026 -0300

    site web e barra de rolagem

[33mcommit 5b325243613eeb1893c6bdb8ad2a46d421be81f8[m
Author: pedroraiher <242241641+pedroraiher@users.noreply.github.com>
Date:   Sun Nov 8 15:07:11 2026 -0300

    reposicionar

[33mcommit c7de9dad28d86fceeded6ded9623b5ae3e5e88f6[m
Author: pedroraiher <242241641+pedroraiher@users.noreply.github.com>
Date:   Wed Nov 4 08:09:34 2026 -0300

    zoom hd

[33mcommit 8953708cd4997a4f0fe387a62e654284bb60d956[m
Author: Pedro <242241641+pedroraiher@users.noreply.github.com>
Date:   Mon Nov 2 16:24:37 2026 -0300

    v2

[33mcommit 29613f45adf9aa4bbf950b13b37dd849c6e275a8[m
Author: Pedro <242241641+pedroraiher@users.noreply.github.com>
Date:   Mon Nov 2 15:42:47 2026 -0300

    codexmod1

[33mcommit 76e71c16c6a89ca9f6e83c623551ffe0816588a7[m
Merge: 81daacc 399248f
Author: Jesse Li-Yates <jjjegly@gmail.com>
Date:   Sat Oct 31 17:37:33 2026 +1100

    Merge pull request #20 from jegly/fix/security-deps
    
    Fix Dependabot security alerts

[33mcommit 399248f08ce8a047d2bf7fa23e345cf532dec3b7[m
Author: jegly <jessesnameistaken@gmail.com>
Date:   Sat Oct 31 17:28:02 2026 +1100

    Fix Dependabot security alerts
    
    Lift AGP 9.5.0's own Guava (33.4.0-jre, high) and commons-lang3
    (3.16.0, moderate) on the build classpath to patched versions.
    
    Pin Guava 33.7.2-android in the app: media3 pulled Guava 33.3.1-android
    into the APK (CVE-2026-102554).
    
    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>

[33mcommit 81daacc18c143c7bab667f933fb3afdcb29a9e13[m
Merge: 45f962b c8aa0a8
Author: Jesse Li-Yates <jjjegly@gmail.com>
Date:   Sat Oct 31 17:08:08 2026 +1100

    Merge pull request #19 from jegly/docs/readme-hashes
    
    README: lay out signing section like Box

[33mcommit c8aa0a88a4a04411c66a10ac69b1b0b7eeace214[m
Author: jegly <jessesnameistaken@gmail.com>
Date:   Sat Oct 31 17:00:18 2026 +1100

    README: lay out signing section like Box
    
    Checksums table, the certificate SHA-256 for Obtainium AppVerifier, and
    a v3.2 signer table with the PQC (ML-DSA-87) and hybrid classical
    fingerprints.
    
    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>

[33mcommit 45f962be94ab9f4a2dedae8d9ec1d37454d8275f[m
Merge: 38ea92e 4484637
Author: Jesse Li-Yates <jjjegly@gmail.com>
Date:   Sat Oct 31 16:57:32 2026 +1100

    Merge pull request #18 from jegly/docs/readme-hashes
    
    README: update release APK and SHA-256 for Gallery-4.2.1

[33mcommit 4484637f57b967fc76dc8b536796374af1bee921[m
Author: jegly <jessesnameistaken@gmail.com>
Date:   Sat Oct 31 16:53:33 2026 +1100

    README: update release APK and SHA-256 for Gallery-4.2.1
    
    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>

[33mcommit 38ea92e83a04035a22d04bfaedec02ccd07543fb[m
Merge: f5ed082 332dbd3
Author: Jesse Li-Yates <jjjegly@gmail.com>
Date:   Sat Oct 31 16:18:30 2026 +1100

    Merge pull request #17 from jegly/ci/release-latest
    
    Release: base the version on the latest published release

[33mcommit 332dbd36c6249c5b503188f4f4c1b0da5fda0ca8[m
Author: jegly <jessesnameistaken@gmail.com>
Date:   Sat Oct 31 16:11:47 2026 +1100

    Release: base the version on the latest published release
    
    Use GitHub's latest release instead of the highest tag. Frequency has
    stray v5.0.x tags from its Tunes days (the v5.0.3 APK is versionName
    0.0.1), which made the next version 5.0.4 instead of 0.2.4. Also refuse
    a version whose tag already exists.
    
    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>

[33mcommit f5ed082e0b0195e01846e185dd81c5f9b761dc62[m
Merge: 5ec1a28 d75736c
Author: Jesse Li-Yates <jjjegly@gmail.com>
Date:   Sat Oct 31 15:47:50 2026 +1100

    Merge pull request #16 from jegly/ci/release-tag
    
    Release: create the tag before the draft release

[33mcommit d75736cbf633c6ffd8d6f02f64313be1ee77bed8[m
Author: jegly <jessesnameistaken@gmail.com>
Date:   Sat Oct 31 15:37:50 2026 +1100

    Release: create the tag before the draft release
    
    Publishing a draft whose tag GitHub still had to create failed with an
    HTTP 500 (immutable releases are on). The workflow now pushes vX.Y.Z
    itself and creates the draft on that tag, so publishing only flips the
    draft. If creating the draft fails, the tag is removed again.
    
    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>

[33mcommit 5ec1a282d0ef5e3a12a37afd9f4c63be365275c7[m
Merge: 13315bc fc94154
Author: Jesse Li-Yates <jjjegly@gmail.com>
Date:   Sat Oct 31 15:12:06 2026 +1100

    Merge pull request #15 from jegly/ci/release
    
    Add release workflow with version from the release tag

[33mcommit fc94154b6cef7f6034ab279be8485e2ea0643a2e[m
Author: jegly <jessesnameistaken@gmail.com>
Date:   Sat Oct 31 14:58:25 2026 +1100

    Add release workflow with version from the release tag
    
    Actions > Release > Run workflow builds an unsigned APK with
    versionName X.Y.Z and versionCode X*10000+Y*100+Z, then attaches it to
    a draft release with generated notes (Dependabot updates grouped).
    Leaving the version empty bumps the patch of the latest version.
    The app reads appVersionCode/appVersionName Gradle properties and
    keeps its current values for local builds.
    
    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>

[33mcommit 13315bcd4c287ce87900b9a90d18b7375e69ed99[m
Merge: 03c6e0b 4781a0a
Author: Jesse Li-Yates <jjjegly@gmail.com>
Date:   Sat Oct 31 14:50:33 2026 +1100

    Merge pull request #14 from jegly/dependabot/gradle/agp-77c1448e0b
    
    Bump com.android.application from 9.4.1 to 9.5.0 in the agp group

[33mcommit 4781a0a02fca20abda6822fb76f4c0b2df3f5c1c[m
Author: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
Date:   Sat Oct 31 03:35:08 2026 +0000

    Bump com.android.application from 9.4.1 to 9.5.0 in the agp group
    
    Bumps the agp group with 1 update: com.android.application.
    
    
    Updates `com.android.application` from 9.4.1 to 9.5.0
    
    ---
    updated-dependencies:
    - dependency-name: com.android.application
      dependency-version: 9.5.0
      dependency-type: direct:production
      update-type: version-update:semver-minor
      dependency-group: agp
    ...
    
    Signed-off-by: dependabot[bot] <support@github.com>

[33mcommit 03c6e0b22e800cbac009e7a5f0816e2f9ce169ba[m
Merge: 88b1b78 8af5013
Author: Jesse Li-Yates <jjjegly@gmail.com>
Date:   Sat Oct 31 14:33:25 2026 +1100

    Merge pull request #13 from jegly/ci/build-check
    
    Add CI build check and GitHub Actions updates

[33mcommit 8af5013ce0753888184b5cd026fa26d43c78af6d[m
Author: jegly <jessesnameistaken@gmail.com>
Date:   Sat Oct 31 14:15:08 2026 +1100

    Add CI build check and GitHub Actions updates
    
    Build the unsigned release APK on pushes to main and on every PR,
    so Dependabot updates are checked before merging. Also let Dependabot
    keep the workflow's action versions current.
    
    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>

[33mcommit 88b1b7871d26a450f06601f04c5972691ca764ff[m
Author: Jesse Li-Yates <jjjegly@gmail.com>
Date:   Tue Oct 27 14:08:47 2026 +1100

    Create robots.txt

[33mcommit 24ed15174b8ff219eae24fc58f6db1d73b447b70[m
Merge: c588b71 a2659a7
Author: Jesse Li-Yates <jjjegly@gmail.com>
Date:   Mon Oct 26 17:21:17 2026 +1100

    Merge pull request #12 from jegly/gradle/nettyVersion-4.2.19.Final
    
    Bump nettyVersion from 4.1.139.Final to 4.2.19.Final

[33mcommit a2659a762c454ff6a966a95b0367d6e294547cde[m
Author: dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
Date:   Sun Oct 25 16:44:37 2026 +0000

    Bump nettyVersion from 4.1.139.Final to 4.2.19.Final
    
    Bumps `nettyVersion` from 4.1.139.Final to 4.2.19.Final.
    
    Updates `io.netty:netty-buffer` from 4.1.139.Final to 4.2.19.Final
    - [Release notes](https://github.c