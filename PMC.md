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

## Release Procedure

1. Generate code signing key if you do not already have one for Apache. Refer to
http://apache.org/dev/openpgp.html#generate-key on how to generate a strong code
signing key.
2. Add your public key to the `KEYS` file at the root of the source code tree.
3. Create a new release branch, with version bumped to the next release version.
  1. `git checkout -b release/0.11.0`
  2. Replace all `0.11.0-SNAPSHOT` in the code tree to `0.11.0-incubating`.
  3. `git commit -am "Prepare 0.11.0-incubating-rc1"`
  4. `git tag -am "Apache PredictionIO (incubating) 0.11.0-rc1" v0.11.0-incubating-rc1`
4. If you have not done so, use SVN to checkout
https://dist.apache.org/repos/dist/dev/incubator/predictionio. This is the area
for staging release candidates for voting.
  1. `svn co https://dist.apache.org/repos/dist/dev/incubator/predictionio`
5.  Package a clean tarball for staging a release candidate.
  1. `git archive --format tar v0.11.0-incubating-rc1 >
  ../apache-predictionio-0.11.0-incubating-rc1.tar`
  2. `cd ..; gzip apache-predictionio-0.11.0-incubating-rc1.tar`
6. Generate detached signature for the release candidate.
(http://apache.org/dev/release-signing.html#openpgp-ascii-detach-sig)
  1. `gpg --armor --output apache-predictionio-0.11.0-incubating-rc1.tar.gz.asc
  --detach-sig apache-predictionio-0.11.0-incubating-rc1.tar.gz`
7. Generate MD5 and SHA512 checksums for the release candidate.
  1. `gpg --print-md MD5 apache-predictionio-0.11.0-incubating-rc1.tar.gz >
  apache-predictionio-0.11.0-incubating-rc1.tar.gz.md5`
  2. `gpg --print-md SHA512 apache-predictionio-0.11.0-incubating-rc1.tar.gz >
  apache-predictionio-0.11.0-incubating-rc1.tar.gz.sha512`
8. Create a subdirectory at the SVN staging area. The area should have a `KEYS` file.
  1. `mkdir apache-predictionio-0.11.0-incubating-rc1`
  2. `cp apache-predictionio-0.11.0-incubating-rc1.*
  apache-predictionio-0.11.0-incubating-rc1`
9. If you have updated the `KEYS` file, also copy that to the staging area.
10. `svn commit`
11. Set up credentials with Apache Nexus using the SBT Sonatype plugin. Put this
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

12. `sbt/sbt +publishSigned +storage/publishSigned
+dataElasticsearch/publishSigned` then close the staged repository on Apache
Nexus.
13. Wait for Travis to pass build on the release branch.
14. Tag the release branch with a rc tag, e.g. `0.11.0-incubating-rc1`.
15. Send out e-mail for voting on PredictionIO dev mailing list.

  ```
  Subject: [VOTE] Apache PredictionIO (incubating) 0.11.0 Release (RC1)

  This is the vote for 0.11.0 of Apache PredictionIO (incubating).

  The vote will run for at least 72 hours and will close on Apr 7th, 2017.

  The release candidate artifacts can be downloaded here: https://dist.apache.org/repos/dist/dev/incubator/predictionio/0.11.0-incubating-rc1/

  Test results of RC5 can be found here: https://travis-ci.org/apache/incubator-predictionio/builds/xxx

  Maven artifacts are built from the release candidate artifacts above, and are provided as convenience for testing with engine templates. The Maven artifacts are provided at the Maven staging repo here: https://repository.apache.org/content/repositories/orgapachepredictionio-nnnn/

  All JIRAs completed for this release are tagged with 'FixVersion = 0.10.0'. You can view them here: https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12320420&version=12337844

  The artifacts have been signed with Key : YOUR_KEY_ID

  Please vote accordingly:

  [ ] +1, accept RC as the official 0.10.0 release
  [ ] -1, do not accept RC as the official 0.10.0 release because...
  ```
