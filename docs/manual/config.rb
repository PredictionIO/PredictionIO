#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

require 'lib/custom_renderer'
require 'lib/gallery_generator'

# General Settings
set :css_dir,       'stylesheets'
set :js_dir,        'javascripts'
set :images_dir,    'images'
set :partials_dir,  'partials'

activate :directory_indexes
activate :syntax, line_numbers: true
activate :autoprefixer

# Markdown
set :markdown_engine, :redcarpet
set :markdown,
    renderer: ::CustomRenderer,
    fenced_code_blocks: true,
    no_intra_emphasis: true,
    autolink: true,
    strikethrough: true,
    superscript: true,
    highlight: true,
    underline: true,
    tables: true

# Sprockets
sprockets.append_path File.join root, 'bower_components'

# Sitemap
set :url_root, '//predictionio.apache.org'
activate :search_engine_sitemap, exclude_attr: 'hidden'

# Development Settings
configure :development do
  set :scheme, 'http'
  set :host, Middleman::PreviewServer.host rescue 'localhost'
  set :port, Middleman::PreviewServer.port rescue 80
  Slim::Engine.set_options pretty: false, sort_attrs: false
  set :debug_assets, true
end

# Build Settings
configure :build do
  set :scheme, 'https'
  set :host, 'predictionio.apache.org'
  set :port, 80
  Slim::Engine.set_options pretty: false, sort_attrs: false
  activate :asset_hash
  activate :minify_css
  activate :minify_javascript
  activate :minify_html do |html|
    html.remove_multi_spaces        = true
    html.remove_comments            = true
    html.remove_intertag_spaces     = false
    html.remove_quotes              = false
    html.simple_doctype             = false
    html.remove_script_attributes   = true
    html.remove_style_attributes    = false
    html.remove_link_attributes     = false
    html.remove_form_attributes     = false
    html.remove_input_attributes    = false
    html.remove_javascript_protocol = true
    html.remove_http_protocol       = false
    html.remove_https_protocol      = false
    html.preserve_line_breaks       = false
    html.simple_boolean_attributes  = false
  end
end

# Hacks

# Engine Template Gallery generation
current_dir = File.dirname(__FILE__)
yaml_file_path = "#{current_dir}/source/gallery/templates.yaml"
out_file_path = "#{current_dir}/source/gallery/template-gallery.html.md"
Gallery.generate_md(yaml_file_path, out_file_path)

# https://github.com/middleman/middleman/issues/612
Slim::Engine.disable_option_validator!

# https://github.com/Aupajo/middleman-search_engine_sitemap/issues/2
set :file_watcher_ignore, [
  /^bin(\/|$)/,
  /^\.bundle(\/|$)/,
  # /^vendor(\/|$)/, # Keep this commented out!
  /^node_modules(\/|$)/,
  /^\.sass-cache(\/|$)/,
  /^\.cache(\/|$)/,
  /^\.git(\/|$)/,
  /^\.gitignore$/,
  /\.DS_Store/,
  /^\.rbenv-.*$/,
  /^Gemfile$/,
  /^Gemfile\.lock$/,
  /~$/,
  /(^|\/)\.?#/,
  /^tmp\//
]
