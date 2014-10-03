/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* Modified version of the Spark Documentation's main.js file */

function codeTabs() {
  var counter = 0;
  $("div.codetabs").each(function() {
    $(this).addClass("tab-content");

    // Insert the tab bar
    var tabBar = $('<ul class="nav nav-tabs" data-tabs="tabs"></ul>');
    $(this).before(tabBar);

    // Add each code sample to the tab bar:
    var codeSamples = $(this).children("div");
    codeSamples.each(function() {
      $(this).addClass("tab-pane");
      var lang = $(this).data("lang");
      var cls = "tab_" + lang.replace(" ", "_")
      var id = cls + "_" + counter;
      $(this).attr("id", id);
      tabBar.append(
        '<li><a class="tab_' + cls + '" href="#' + id + '"><b>' + lang + '</b></a></li>'
      );
    });

    codeSamples.first().addClass("active");
    tabBar.children("li").first().addClass("active");
    counter++;
  });
  $("ul.nav-tabs a").click(function (e) {
    // Toggling a tab should switch all tabs corresponding to the same language
    // while retaining the scroll position
    e.preventDefault();
    var scrollOffset = $(this).offset().top - $(document).scrollTop();
    $("." + $(this).attr('class')).tab('show');
    $(document).scrollTop($(this).offset().top - scrollOffset);
  });
}

$(function() {
	  codeTabs();
});
