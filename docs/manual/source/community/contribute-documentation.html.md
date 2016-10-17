---
title: Contribute Documentation
---

## How to Write Documentation

You can help improve the Apache PredictionIO (incubating) documentation by
submitting tutorials, writing how-tos, fixing errors, and adding missing
information. You can edit any page live on
[GitHub](https://github.com/apache/incubator-predictionio) by clicking the
pencil icon on any page or open a [Pull
Request](https://help.github.com/articles/creating-a-pull-request/).

## Branching

Use the `livedoc` branch if you want to update the current documentation.

Use the `develop` branch if you want to write documentation for the next
release.

## Installing Locally

Apache PredictionIO (incubating) documentation uses
[Middleman](http://middlemanapp.com/) and is hosted on Apache.

[Gems](http://rubygems.org/) are managed with [Bundler](http://bundler.io/).
Front end code with [Bower](http://bower.io/).

Requires [Ruby](https://www.ruby-lang.org/en/) 2.1 or greater. We recommend
[RVM](http://rvm.io/) or [rbenv](https://github.com/sstephenson/rbenv).

WARNING: **OS X** users you will need to install [Xcode Command Line
Tools](https://developer.apple.com/xcode/downloads/) with: `$ xcode-select
--install` first.

You can install everything with the following commands:

```bash
$ cd docs/manual
$ gem install bundler
$ bundle install
$ npm install -g bower
$ bower install
```


## Starting the Server

Start the server with:

```
$ bundle exec middleman server
```

This will start the local web server at [localhost:4567](http://localhost:4567/).

## Building the Site

Build the site with:

```
$ bundle exec middleman build
```

## Styleguide

Please follow this styleguide for any documentation contributions.

### Text

View our [Sample Typography](/samples/) page for all posible styles.

### Headings

The main heading `h1` is derived from the title data attribute:

```
---
title: Page Title
---
```

Start other headings with `h2`. Prefer the `## Heading` format in Markdown.

### Links

Internal links:

* Should start with / (relative to root).
* Should end with / (S3 requirement).
* Should **not** end with .html.

Following these rules helps keep everything consistent and allows our version
parser to correctly version links. Middleman is configured for directory
indexes. Linking to a file in `sources/samples/index.html` should be done with
`[Title](/sample/)`.

```md
[Good](/path/to/page/)

[Bad](../page) Not relative to root!
[Bad](page.html) Do not use the .html extension!
[Bad](/path/to/page) Does not end with a /.

```

### Images

Images should be exactly 900px wide. [Chrome Window Resizer](https://chrome.google.com/webstore/detail/window-resizer/kkelicaakdanhinjdeammmilcgefonfh)
is an excellent extension for browser resizing.

WARNING: **OS X** users please [Disable Shadows](http://www.idownloadblog.com/2014/08/03/how-to-remove-the-shadow-window-screenshots-on-mac-os-x/)
before taking a screenshot.

Images should only show the relevant tab/terminal. Hide any additional toolbars.

Images will **automatically scale** by default. If you want an image to remain a set size you can use a raw HTML tag like this:

```
<img src="/images/path/to/image.png" alt="Image" class="static" />
```

### Code Blocks

Fenced code blocks are created using the <code>&#96;&#96;&#96;language</code> format.

A example of each language is available on our [Language Samples](/samples/languages) page.

### Code Tabs

Code tabs use the following HTML format:

```html
<div class="tabs">
  <div data-tab="Tab Title" data-lang="language">
    Markdown, code blocks, or HTML is OK inside a tab.
  </div>
  <div data-tab="Second Tab" data-lang="optional">
    ...
  </div>
</div>
```

You can see an example of this on our [Tab Samples](/samples/tabs/) page.

### SEO

You can hide a page from the `sitemap.xml` file by setting the pages
[Frontmater](http://middlemanapp.com/basics/frontmatter/) like this:

```md
---
title: Secret Page
hidden: true
---
```

## Important Files

| Description   | File          |
| ------------- | ------------- |
| Left side navigation. | `data/nav/main.yml` |
| Main site layout. | `source/layouts/layout.html.slim` |
| Custom Markdown renderer based on [Redcarpet](https://github.com/vmg/redcarpet). | `lib/custom_renderer.rb` |
| Custom TOC helper. | `helpers/table_of_contents_helpers.rb` |

### Versions

Various site wide versions are defined in `data/versions.yml` and embedded with ERB like `<%= data.versions.pio %>`.

NOTE: Files must end with a `.erb` extension to be processed as ERB.

## Going Live

Pushing to the `livedoc` branch will update
http://predictionio.incubator.apache.org in about 5 minutes.

You can check the progress of each build on [Apache's
Jenkins](https://builds.apache.org/).

```
$ git push origin livedoc
```

## Checking the Site

WARNING: The check rake task is still in **beta** however it is extremely useful for catching accidental errors.

```bash
$ bundle exec middleman build
$ bundle exec rake check
```

The `rake check` task parses each HTML page in the `build` folder and checks it for common errors including 404s.

## License

Documentation is under a [Apache License Version
2.0](https://www.apache.org/licenses/LICENSE-2.0).
