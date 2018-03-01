<!--
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Project Management Committee Documentation

This outlines the steps for a PMC member to create a new release. More details
and policy guidelines can be found here: http://www.apache.org/dev/release-distribution

## Release Procedure

1. Generate code signing key if you do not already have one for Apache. Refer to
http://apache.org/dev/openpgp.html#generate-key on how to generate a strong code
signing key.
2. Add your public key to the `KEYS` file at the root of the source code tree.
3. Create a new release branch, with version bumped to the next release version.
    * `git checkout -b release/0.13.0`
    * Replace all `0.13.0-SNAPSHOT` in the code tree to `0.13.0`.
    * `git commit -am "Prepare 0.13.0-rc1"`
    * `git tag -am "Apache PredictionIO 0.13.0-rc1" v0.13.0-rc1`
4. If you have not done so, use SVN to checkout
https://dist.apache.org/repos/dist/dev/predictionio. This is the area
for staging release candidates for voting.
    * `svn co https://dist.apache.org/repos/dist/dev/predictionio`
5.  Package a clean tarball for staging a release candidate.
    * `git archive --format tar v0.13.0-rc1 >
  ../apache-predictionio-0.13.0-rc1.tar`
    * `cd ..; gzip apache-predictionio-0.13.0-rc1.tar`
6. Generate detached signature for the release candidate.
(http://apache.org/dev/release-signing.html#openpgp-ascii-detach-sig)
    * `gpg --armor --output apache-predictionio-0.13.0-rc1.tar.gz.asc
  --detach-sig apache-predictionio-0.13.0-rc1.tar.gz`
7. Generate MD5 and SHA512 checksums for the release candidate.
    * `gpg --print-md MD5 apache-predictionio-0.13.0-rc1.tar.gz >
  apache-predictionio-0.13.0-rc1.tar.gz.md5`
    * `gpg --print-md SHA512 apache-predictionio-0.13.0-rc1.tar.gz >
  apache-predictionio-0.13.0-rc1.tar.gz.sha512`
8. Run `./make-distribution.sh` and repeat steps 5 to 7 to create binary distribution release.
9. Create a subdirectory at the SVN staging area. The area should have a `KEYS` file.
    * `mkdir apache-predictionio-0.13.0-rc1`
    * `cp apache-predictionio-0.13.0-rc1.*
  apache-predictionio-0.13.0-rc1`
10. If you have updated the `KEYS` file, also copy that to the staging area.
11. `svn commit`
12. Set up credentials with Apache Nexus using the SBT Sonatype plugin. Put this
in `~/.sbt/0.13/sonatype.sbt`. You can generate username and password tokens
from ASF's Nexus instance.

  ```
  publishTo := {
      val nexus = "https://repository.apache.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }

  credentials += Credentials("Sonatype Nexus Repository Manager", "repository.apache.org", "username_token", "password_token")
  ```
13. `sbt/sbt +publishSigned +storage/publishSigned
+dataElasticsearch/publishSigned` then close the staged repository on Apache
Nexus.
    * You may need to run `sbt/sbt publishLocal` first to avoid depedency errors.
14. Wait for Travis to pass build on the release branch.
15. Tag the release branch with a rc tag, e.g. `0.13.0-rc1`.
16. Send out email for voting on PredictionIO dev mailing list.

  ```
  Subject: [VOTE] Apache PredictionIO 0.13.0 Release (RC1)

  This is the vote for 0.13.0 of Apache PredictionIO.

  The vote will run for at least 72 hours and will close on Apr 7th, 2017.

  The release candidate artifacts can be downloaded here: https://dist.apache.org/repos/dist/dev/predictionio/0.13.0-rc1/

  Test results of RC5 can be found here: https://travis-ci.org/apache/predictionio/builds/xxx

  Maven artifacts are built from the release candidate artifacts above, and are provided as convenience for testing with engine templates. The Maven artifacts are provided at the Maven staging repo here: https://repository.apache.org/content/repositories/orgapachepredictionio-nnnn/

  All JIRAs completed for this release are tagged with 'FixVersion = 0.13.0'. You can view them here: https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12320420&version=12337844

  The artifacts have been signed with Key : YOUR_KEY_ID

  Please vote accordingly:

  [ ] +1, accept RC as the official 0.13.0 release
  [ ] -1, do not accept RC as the official 0.13.0 release because...
  ```
17. After the vote has been accepted, use SVN to checkout
https://dist.apache.org/repos/dist/release/predictionio/. This is the area
for staging actual releases.
18. Repeat steps 5 to 7 to create the official release, and step 13 to publish it.
Also, remove old releases from the ASF distribution mirrors.
19. Document breaking changes in http://predictionio.apache.org/resources/upgrade/.
20. Update `RELEASE.md`.
21. Send out an email to the following mailing lists: announce, general, user, dev.

  ```
  Subject: [ANNOUNCE] Apache PredictionIO 0.13.0 Release

  The Apache PredictionIO team would like to announce the release of Apache
  PredictionIO 0.13.0.

  Release notes are here:
  https://github.com/apache/predictionio/blob/release/0.13.0/RELEASE.md

  Apache PredictionIO is an open source Machine Learning Server built on
  top of state-of-the-art open source stack, that enables developers to
  manage and deploy production-ready predictive services for various kinds
  of machine learning tasks.

  More details regarding Apache PredictionIO can be found here:
  http://predictionio.apache.org/

  The release artifacts can be downloaded here:
  https://dist.apache.org/repos/dist/release/predictionio/0.13.0/

  All JIRAs completed for this release are tagged with 'FixVersion =
  0.13.0'; the JIRA release notes can be found here:
  https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12320420&version=12337844

  Thanks!
  The Apache PredictionIO Team
  ```
