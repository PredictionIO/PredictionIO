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
title: Tabs
hidden: true
---

This page is used to test the tabs plugin based on [Tabslet](https://github.com/vdw/Tabslet).

<div class="tabs">
  <div data-tab="Ruby" data-lang="ruby">
```ruby
# This is a ruby file.
class MyClass
  def foo
    'bar'
  end
end
```
  </div>
  <div data-tab="Plain">
This is a test of **markdown** inside a tab!

```
// This tab does not have the data-lang attribute set!
$ cd path/to/your/file
```
  </div>
  <div data-tab="HTML" data-lang="html">
```html
<p>Yes you can still use HTML in code blocks!</p>
```
  </div>
  <div data-tab="Test">
```php
Test 0 <>
Test 1 >
Test 3 <
Test 4 ><
Test 5 =>
Test 6 <=
Test 7 <>
<p><b>Test</b></p>
```
  </div>
</div>

## Test Syncing

Here we show a similar set of tabs to test language syncing:

<div class="tabs">
  <div data-tab="Ruby" data-lang="ruby">
```ruby
# This is a ruby file.
class MyClass
  def foo
    'bar'
  end
end
```
  </div>
  <div data-tab="Plain">
This is a test of **markdown** inside a tab!

```
// This tab does not have the data-lang attribute set!
$ cd path/to/your/file
```
  </div>
  <div data-tab="HTML" data-lang="html">
<p>This HTML is <b>hard coded</b>.</p>
  </div>
  <div data-tab="Python" data-lang="python">
```python
# The other group does not have a Python tab.
```
  </div>
</div>

<div class="tabs">
  <div data-tab="Java" data-lang="java">
```Java
// Java code..
```
  </div>
  <div data-tab="HTML" data-lang="html">
  
  This includes **bold** with Markdown.
  
  </div>
</div>