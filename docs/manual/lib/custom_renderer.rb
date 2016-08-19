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

require 'middleman-core/renderers/redcarpet'

class CustomRenderer < Middleman::Renderers::MiddlemanRedcarpetHTML
  def initialize(options = {})
    defaults = { with_toc_data: true }
    super(defaults.merge(options))
  end

  def paragraph(text)
    case text
    when/\A(INFO|SUCCESS|WARNING|DANGER|NOTE|TODO):/
      convert_alerts(text)
    else
      %Q(<p>#{text}</p>)
    end
  end

  def header(text, level)
    id = text.downcase.tr(" ", "-")
    id = "'" + id + "'"
    #the anchor before the headings are there to provide proper jumping points.
    "<h#{level} id=#{id} class='header-anchors' >#{text}</h#{level}>"
  end

  def block_code(code, language)
    language = language ? language : 'bash'
    super
  end

  def block_html(raw_html)
    # Render fenced code blocks first!
    replace = raw_html.gsub(/(```.*?```)/m) do |match|
      markdown = Redcarpet::Markdown.new(CustomRenderer, fenced_code_blocks: true)
      markdown.render(match)
    end

    doc = Nokogiri::HTML::DocumentFragment.parse(replace)
    nodes = doc.css('div.tabs > div')

    if nodes.empty?
      raw_html
    else
      ul = Nokogiri::XML::Node.new('ul', doc)
      ul['class'] = 'control'

      nodes.each do |node|
        title = node.attribute('data-tab').to_s
        lang = node.attribute('data-lang').to_s

        uuid = SecureRandom.uuid
        id = "tab-#{uuid}"

        li = Nokogiri::XML::Node.new('li', doc)
        li['data-lang'] = lang
        li.inner_html = %Q(<a href="##{id}">#{title}</a>)

        ul.add_child(li)

        node['id'] = id
      end

      nodes.first.before(ul)

      doc.to_html
    end
  end

  private

  def convert_alerts(text)
    text.gsub(/\A(INFO|SUCCESS|WARNING|DANGER|NOTE|TODO):(.*?)(\n(?=\n)|\z)/m) do
      css_class = $1.downcase
      content = $2.strip
      %Q(<div class="alert-message #{css_class}"><p>#{content}</p></div>)
    end
  end
end