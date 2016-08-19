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

module TableOfContentsHelpers
  def table_of_contents(resource)
    content = remove_front_matter_data(File.read(resource.source_file))
    extension = File.extname(resource.source_file)[1..-1] # Trim the first dot.

    if extension != 'md'
      # Render other extensions first if they exist.
      template = Tilt[extension].new { content }
      content = template.render(self, resource.data)
    end

    # Now the custom Markdown TOC.
    markdown = Redcarpet::Markdown.new(Redcarpet::Render::HTML_TOC.new(nesting_level: 2))
    # TOC gets confused with Ruby comments inside code blocks so we removed them.
    content_without_code = content.gsub(/(```[\s\S]*?```)/, '')
    output = markdown.render(content_without_code)

    if output.length == 0
      return
    else
      content_tag :aside, output, id: 'table-of-contents'
    end
  end

  private

  def remove_front_matter_data(content)
    yaml_regex = /\A(---\s*\n.*?\n?)^((---|\.\.\.)\s*$\n?)/m
    if content =~ yaml_regex
      content = content.sub(yaml_regex, '')
    end

    json_regex = /\A(;;;\s*\n.*?\n?)^(;;;\s*$\n?)/m
    if content =~ json_regex
      content = content.sub(json_regex, '')
    end

    content
  end
end
