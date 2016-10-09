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

---
title:  Contribute Code
---

Thank you for your interest in contributing to Apache PredictionIO (incubating).
Our mission is to enable developers to build scalable machine learning
applications easily. Here is how you can help with the project development. If
you have any question regarding development at anytime, please free to
[subscribe](mailto:dev-subscribe@predictionio.incubator.apache.org) and post to
the [Development Mailing
List](mailto:dev-subscribe@predictionio.incubator.apache.org).

## Areas in Need of Help

We accept contributions of all kinds at any time. We are compiling this list to
show features that are highly sought after by the community.

- Tests and CI
- Engine template, tutorials, and samples
- Client SDKs
- Building engines in Java (updating the Java controller API)
- Code clean up and refactoring
- Code and data pipeline optimization
- Developer experience (UX) improvement

## How to Report an Issue

If you wish to report an issue you found, you can do so on [Apache PredictionIO
(incubating) JIRA](https://issues.apache.org/jira/browse/PIO).

## How to Help Resolve Existing Issues

In general, bug fixes should be done the same way as new features, but critical
bug fixes will follow a different path.

## How to Add / Propose a New Feature

1. To propose a new feature, simply
   [subscribe](mailto:dev-subscribe@predictionio.incubator.apache.org) and post
   your proposal to [Apache PredictionIO (incubating) Development Mailing List]
   (mailto:dev@predictionio.incubator.apache.org).
2. Discuss with the community and the core development team on what needs to be
   done, and lay down concrete plans on deliverables.
3. Once solid plans are made, start creating tickets in the [issue tracker]
   (https://issues.apache.org/jira/browse/PIO).
4. Work side by side with other developers using Apache PredictionIO
   (incubating) Development Mailing List as primary mode of communication. You
   never know if someone else has a better idea. ;)


## How to Issue a Pull Request

When you have finished your code, you can [create a pull
request](https://help.github.com/articles/creating-a-pull-request/) against the
**develop** branch.

- The title must contain a tag associating with an existing JIRA ticket. You
  must create a ticket so that the infrastructure can correctly track issues
  across Apache JIRA and GitHub. If your ticket is `PIO-789`, your title must
  look something like `[PIO-789] Some short description`.
- Please also, in your commit message summary, include the JIRA ticket number
  similar to above.
- Make sure the title and description are clear and concise. For more details on
  writing a good commit message, check out [this
  guide](http://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html).
- If the change is visual, make sure to include a screenshot or GIF.
- Make sure it is being opened into the right branch.
- Make sure it has been rebased on top of that branch.

NOTE: When it is close to a release, and if there are major development ongoing,
a release branch will be forked from the develop branch to stabilize the code
for binary release. Please refer to the *git flow* methodology page for more
information.

## Getting Started

Apache PredictionIO (incubating) relies heavily on the [git flow methodology](
http://nvie.com/posts/a-successful-git-branching-model/). Please make sure you
read and understand it before you start your development. By default, cloning
Apache PredictionIO (incubating) will put you in the *develop* branch, which in
most cases is where all the latest development go to.

NOTE: For core development, please follow the [Scala Style Guide](http://docs.scala-lang.org/style/).

### Create a Fork of the Apache PredictionIO (incubating) Repository

1. Start by creating a GitHub account if you do not already have one.
2. Go to [Apache PredictionIO (incubating)’s GitHub
   mirror](https://github.com/PredictionIO/PredictionIO) and fork it to your own
   account.
3. Clone your fork to your local machine.

If you need additional help, please refer to
https://help.github.com/articles/fork-a-repo/.

### Building Apache PredictionIO (incubating) from Source

After the previous section, you should have a copy of Apache PredictionIO
(incubating) in your local machine ready to be built.

1. Make sure you are on the *develop* branch. You can double check by `git
   status` or simply `git checkout develop`.
2. At the root of the repository, do `./make-distribution.sh` to build
   PredictionIO.

### Setting Up the Environment

Apache PredictionIO (incubating) relies on 3rd party software to perform its
tasks. To set them up, simply follow this [documentation](
http://docs.prediction.io/install/install-sourcecode/#installing-dependencies).

### Start Hacking

You should have a Apache PredictionIO (incubating) development environment by
now. Happy hacking!

## Anatomy of Apache PredictionIO (incubating) Code Tree

The following describes each directory’s purpose.

### bin

Shell scripts and any relevant components to go into the binary distribution.
Utility shell scripts can also be included here.

### conf

Configuration files that are used by both a source tree and binary distribution.

### core

Core Apache PredictionIO (incubating) code that provides the DASE controller
API, core data structures, and workflow creation and management code.

### data

Apache PredictionIO (incubating) Event Server, and backend-agnostic storage
layer for event store and metadata store.

### docs

Source code for http://predictionio.incubator.apache.org site, and any other
documentation support files.

### engines

Obsolete built-in engines. To be removed.

### examples

Complete code examples showing Apache PredictionIO (incubating)'s application.

### sbt

Embedded SBT (Simple Build Tool) launcher.

### templates

Starting point of building your custom engine.

### tools

Tools for running Apache PredictionIO (incubating). Contains primarily the CLI
(command-line interface) and its supporting code, and the experimental
evaluation dashboard.
