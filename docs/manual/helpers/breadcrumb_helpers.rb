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

require 'rainbow/ext/string'

module BreadcrumbHelpers

  def breadcrumbs
    result = false
    data.nav.main.root.each do |node|
      result = breadcrumb_search(current_page.url, node)
      break if result
    end

    partial 'nav/breadcrumbs', locals: { crumbs: result }

  end


  def breadcrumb_search(path, node, depth = 0, crumb = [])
    crumb[depth] = node
    return crumb if node.url == path

    if node.children
      node.children.each do |child|
        result = breadcrumb_search(path, child, depth + 1, crumb)
        return result if result
      end
    end

    false
  end
end
