//= require 'jquery'
//= require 'Tabslet'
//= require 'jcarousel'

// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

window.onresize = function() {
  adjustContentImageWidth();
}

$(document).ready(function() {

  adjustContentImageWidth();

  // header menu toggler
  $('#drawer-toggle').click(function() {
    toggleDrawer(
      document.getElementById('drawer-toggle'),
      document.getElementById('menu-wrapper')
    );
  })

  // mobile nav menu toggler
  $(".mobile-left-menu-toggler").click(function() {
    var isActive = $('#left-menu-indicator').hasClass('active');
    if (isActive) {
    $('#left-menu-indicator').attr("src", '/images/icons/down-arrow.png');
    } else {
      $('#left-menu-indicator').attr("src", '/images/icons/up-arrow.png');
    }
    $('#left-menu-wrapper').toggleClass('active');
    $('#left-menu-indicator').toggleClass('active');
  })

  // search box toggler
  $('.search-box-toggler').click(function() {
    $('.search-form').toggleClass('active');
    $('.st-search-input').focus();
  })

  $('.st-search-input').focusout(function() {
    $('.search-form').toggleClass('active');
  })

  var toggleDrawer = function(icon, menu){
    if (menu.classList.contains("active")) {
      icon.classList.remove("active");
      menu.classList.remove("active");
    } else {
      icon.classList.add("active");
      menu.classList.add("active");
    }
  }

  // mobile search box toggler
  $('.mobile-search-bar-toggler').click(function() {
    $('.swiftype-wrapper').addClass('active');
    $('.st-search-input').focus();
  });

  $('.swiftype-row-hider').click(function() {
    $('.swiftype-wrapper').removeClass('active');
  })

  // add function call to subscription form
  $( "form.ajax-form" ).each(function( index ) {
    $(this).ajaxForm();
  });

  function navExpand(link) {
    link.removeClass('expandible').addClass('collapsible');
    link.children('i').removeClass('fa-caret-right').addClass('fa-caret-down');
    link.next('ul').show();
  }

  function navCollapse(link) {
    link.removeClass('collapsible').addClass('expandible');
    link.children('i').removeClass('fa-caret-down').addClass('fa-caret-right');
    link.next('ul').hide();
  }

  // Main Navigation
  $('#nav-main a').on('click', function(event) {
    var $this = $(this);

    if ($this.hasClass('expandible')) {
      navExpand($this);
      event.preventDefault();
    } else if ($this.hasClass('collapsible')) {
      navCollapse($this);
      event.preventDefault();
    }
  });

  $('#nav-main .active').parentsUntil('#nav-main').each(function() {
    $(this).children('.expandible').each(function() {
      var $this = $(this);
      navExpand($this);
    });
  });

  $('#content').on('click', function(event) {
    $('body').removeClass('active-navigation')
  });

  $('#active-navigation').on('click', function(event) {
    event.preventDefault();
    $('body').toggleClass('active-navigation')
  });

  if ($('#table-of-contents').is(':empty')) {
    $('#table-of-contents').addClass('empty')
  }

  // Tabslet
  $('.tabs').tabslet();

  // Tab Syncing
  $('.control li').on('mousedown', function(event) {
    lang = $(this).data('lang')
    $('.control li[data-lang="' + lang + '"]').each(function() {
      $(this).children('a:first').trigger('click')
    });
  });

  // External Links
  $("a[href^='http']").each(function() {
    $(this).click(function(event) {
      event.preventDefault();
      window.open(this.href);
    }).addClass('external');
  });
});

//ajax form submit
$.fn.ajaxForm = function() {
  var $form, request, $result, emailRegex, $submitInput;
  $form = $(this);
  $result = $form.find('.result');
  $submitInput = $form.find("input[type=submit]");
  emailRegex = /^[a-zA-Z0-9.!#$%&'*+\/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;

  $form.submit(function(event) {
    event.preventDefault();
    var validationMessage, valid = true;

    $.each([ '.required', 'input[type=email]', '[data-match-string]'], function( i, value ) {
      $form.find(value).each(function(j){
        $(this).removeClass('error');
        var result = null;
        switch(i) {
          case 0:
            result = validateRequired($(this));
            break;
          case 1:
            result = validateEmail($(this));
            break;
          case 2:
            result = validateMatch($(this));
            break;
          default:
            break;
        }
        if (result && result['pass'] === false) {
          $(this).addClass('error');
          valid = false;
          validationMessage = result['errorMessage'];
        }
      });
      return valid;
    });

    if (!valid) {
      $result.addClass('error');
      $result.text(validationMessage);
      return;
    }

    $result.removeClass('error');
    $result.text('');
    if (request) {
      request.abort();
    }

    var $inputs = $form.find("input, select, button, textarea");
    var serializedData = $form.serialize();
    $submitInput.val($submitInput.data('state-loading'));
    disableForm();
    request = $.ajax({
      url: $form.attr('action'),
      type: "POST",
      dataType: "jsonp",
      crossDomain: true,
      data: serializedData+ "&prefix=formCallBack",
      jsonpCallback: "formCallBack",
      success: function(data) {
        if (data && data.result == "success") {
          onFormSubmitSuccess();
        } else {
          onFormSubmitError();
          console.error("error: ", data);
        }
      },
      error: function(jqXHR, textStatus, errorThrown) {
        onFormSubmitError();
        console.error(
          "error: "+ textStatus, errorThrown
        );
      }
    });

    function enableForm() {
      $inputs.prop("disabled", false);
    };

    function disableForm() {
      $inputs.prop("disabled", true);
    };

    function onFormSubmitSuccess() {
      $submitInput.val($submitInput.data('state-sucess'));
    };

    function onFormSubmitError() {
      $submitInput.val($submitInput.data('state-normal'));
      enableForm();
      $result.addClass('error');
      $result.html('Oops! An error has occurred.');
    }
  });

  function validateRequired($input) {
    if (!$input.val()) {
      return {
        'pass': false,
        'errorMessage': 'Please fill out all required fields.'
      }
    } else {
      return {
        'pass': true
      }
    }
  };

  function validateEmail($input) {
    if ($input.val() && !emailRegex.test($input.val())) {
      return {
        'pass': false,
        'errorMessage': 'Please input valid email address.'
      }
    } else {
      return {
        'pass': true
      }
    }
  };

  function validateMatch($input) {
    if ($input.val() !== $input.data('match-string')) {
      return {
        'pass': false,
        'errorMessage': "Input doesn't match."
      }
    } else {
      return {
        'pass': true
      }
    }
  };
};

function formCallBack(data) {};

var adjustContentImageWidth = function() {
  // prevent image in place of table of content getting squeezed to next row
  var tableOfContent = document.getElementById('table-of-content-wrapper');
  var rect = tableOfContent.getBoundingClientRect();

  $('.content img').each(function() {
    var withinTableOfContentRow = this.getBoundingClientRect().top > rect.bottom;
    if (withinTableOfContentRow) {
      $(this).addClass('default-width');
    } else {
      $(this).removeClass('default-width');
    }
  })
}
