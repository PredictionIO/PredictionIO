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

require 'yaml'
require 'uri'

module Gallery

  private

  INTRO =
'---
title: Engine Template Gallery
---
'

  UNSUPERVISED = '## Unsupervised Learning '

  CLASSIFICATION = '## Classification'

  REGRESSION = '## Regression'

  RECOMMENDER_SYSTEMS = '## Recommender Systems'

  NLP = '## Natural Language Processing'

  OTHER = '## Other'

  TEMPLATE_INTRO = '
***[%{name}](%{repo})***  '

  STAR_BUTTON =
'
<iframe src="https://ghbtns.com/github-btn.html?user=%{user}&repo=%{repo}&type=star&count=true"
frameborder="0" align="middle" scrolling="0" width="170px" height="20px"></iframe>

'

  TEMPLATE_DETAILS =
'
%{description}

Type | Language | License | Status | PIO min version
:----: | :-----:| :-----: | :----: | :-------------:
%{type} | %{language} | %{license} | %{status} | %{pio_min_version}
<br/>
'

  SECTION_SEPARATOR =
'
<br/>
'

  class Template
    public
    attr_accessor :has_github, :github_repo, :github_user

    def initialize(engine)
      engine.each do |key, val|
        self.instance_variable_set("@#{key}", val)
        self.class.send :define_method, key, lambda { self.instance_variable_get("@#{key}") }
      end

      @tags = @tags.map{ |s| s.downcase }

      @has_github = parse_github
    end

    private
    def parse_github
      uri = URI.parse(@repo)
      if uri.host == 'github.com'
        path = uri.path.split('/')
        raise "Wrong github repo url" unless path.length >= 3
        @github_user = path[1]
        @github_repo = path[2]
        return true
      else
        return false
      end
    end
  end

  def self.write_template(mdfile, template)
    intro = TEMPLATE_INTRO % {
      name: template.name,
      repo: template.repo }

    if template.has_github
        intro += STAR_BUTTON % {
                    user: template.github_user,
                    repo: template.github_repo}
    end

    mdfile.write(intro)
    mdfile.write(TEMPLATE_DETAILS % {
      description: template.description,
      type: template.type,
      language: template.language,
      license: template.license,
      status: template.status,
      pio_min_version: template.pio_min_version,
    })
  end

  def self.write_templates(mdfile, templates)
    templates.each do |t|
      write_template(mdfile, t)
    end
  end

  def self.write_markdown(mdfile, templates)
    classification = templates.select{ |engine| engine.tags.include? 'classification' }
    regression     = templates.select{ |engine| engine.tags.include? 'regression' }
    unsupervised   = templates.select{ |engine| engine.tags.include? 'unsupervised' }
    recommenders   = templates.select{ |engine| engine.tags.include? 'recommender' }
    nlps           = templates.select{ |engine| engine.tags.include? 'nlp' }
    others         = templates.select{ |engine| engine.tags.include? 'other' }

    mdfile.write(INTRO)

    mdfile.write(CLASSIFICATION)
    write_templates(mdfile, classification)

    mdfile.write(SECTION_SEPARATOR)

    mdfile.write(REGRESSION)
    write_templates(mdfile, regression)

    mdfile.write(SECTION_SEPARATOR)

    mdfile.write(UNSUPERVISED)
    write_templates(mdfile, unsupervised)

    mdfile.write(SECTION_SEPARATOR)

    mdfile.write(RECOMMENDER_SYSTEMS)
    write_templates(mdfile, recommenders)

    mdfile.write(SECTION_SEPARATOR)

    mdfile.write(NLP)
    write_templates(mdfile, nlps)

    mdfile.write(OTHER)
    write_templates(mdfile, others)
  end


  public
  def self.generate_md(yaml_file_path, out_file_path)

    File.open(yaml_file_path) do |in_file|
      File.open(out_file_path, 'w') do |out_file|

        templates = YAML.load(in_file)
        parsed = templates.map{ |t| Template.new(t["template"]) }

        write_markdown(out_file, parsed)
      end
    end
  end
end
