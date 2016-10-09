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
title: Sample Style Page
---

## Alerts

### Info

Markdown: `INFO: This is a info message!` will display this:

INFO: This is a info message!

### Success

Markdown: `SUCCESS: This is a success message!` will display this:

SUCCESS: This is a success message!

### Warning

Markdown: `WARNING: This is a warning message!` will display this:

WARNING: This is a warning message!

### Danger

Markdown: `DANGER: This is a danger message!` will display this:

DANGER: This is a danger message!

### Note

Markdown: `NOTE: This is a note message!` will display this:

NOTE: This is a note message!

### TODO

Markdown: `TODO: This is a TODO message!` will display this:

TODO: This is a TODO message!
This message is longer to demonstrate what a multi line message would look like.
This message is longer to demonstrate what a multi line message would look like.
This message is longer to demonstrate what a multi line message would look like.
Yes **bold** and other styling still work inside alerts!


## Text

This is the normal paragraph font.
This is a [internal link](/samples/tabs).
This is an [external link](http://google.com/)
This is a [secure external link](https://google.com/)
This is **bold**.
This is *italic*.
This is _underlined_.
This is ==highlighted==.
This is ~~strikethough~~.
This is ^(superscript).

This is another paragraph.

View [additional sizing](/samples/sizing) samples.

## Lists

* Bullet 1
* Bullet 2
  * Bullet 2.1
  * Bullet 2.2
* Bullet 3

1. First item
2. Second item
3. Third item

## Code

### Block

This is a Scala code block:

```scala
case class Query(
  user: Int,
  num: Int
) extends Serializable

```

See a full list of [supported languages](/samples/languages).


### Inline

This `code is inline`.

## Image

![Sample Image](/images/tutorials/rails/localhost-8000.png)


## Quotes

> This is a blockquote. Don't use these for anything other actual quotes! Use [alerts](#alerts) instead.

## Tables

| First Header  | Second Header |
| ------------- | ------------- |
| Content Cell  | Content Cell  |
| Content Cell  | Content Cell  |

## Other

This is a horizontal rule:

---

This is a en dash &ndash; and an em dash &mdash; using HTML entities.

<div>This is inside a div tag.</div>

# Heading 1

This is the normal paragraph font.

## Heading 2

This is the normal paragraph font.

### Heading 3

This is the normal paragraph font.

#### Heading 4

This is the normal paragraph font.

##### Heading 5

This is the normal paragraph font.

###### Heading 6

This is the normal paragraph font.
