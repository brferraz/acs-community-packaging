/*
 * Prerequisites: mootools.v1.1.js
 */
var OfficeAddin = 
{
   ANIM_LENGTH: 300,
   STATUS_FADE: 10000,
   LIST_DEF_HEIGHT: 204,
   
   init: function()
   {
      window.queryObject = OfficeAddin.toQueryObject(document.location.search);
      window.contextPath = OfficeAddin.getContextPath();
      window.serviceContextPath = window.contextPath + "/wcservice";

      /* Update needed after page load? */      
      if(this.queryObject.st)
      {
         var objResponse = Json.evaluate(this.queryObject.st);
         var imgSuccess = (objResponse.statusCode ? "action_successful.gif" : "action_failed.gif");
         OfficeAddin.showStatusText(objResponse.statusString, imgSuccess, true);
      }
      
      OfficeAddin.makeExternalLinks();
   },

   makeExternalLinks: function(e)
   {
      $$('a').each(function(anchor, i)
      {
         if (anchor.getProperty('rel') == '_blank')
         {
            anchor.target = "_blank";
         }
      });
   },

   toQueryObject: function(s)
   {
      var obj = {};
      $A(s.substring(1).split('&')).each(function(param)
      {
         param = decodeURIComponent(param).split("=");
         obj[param[0]] = param[1];
      });
      return obj;
   },

   showStatusText: function(statusText, statusImage, fadeOut)
   {
      var e = $("statusText");
      if (statusImage)
      {
         statusText = "<img src=\"../../images/office/" + statusImage + "\" alt=\"*\" style=\"padding-right: 8px;\" align=\"top\" />" + statusText;
      }
      e.innerHTML = statusText;
      e.setStyle("opacity", "1");
      if (statusText == "")
      {
         e.setStyle("border-top", "");
      }
      else
      {
         e.setStyle("border-top", "1px dashed #808080");
         var fx = new Fx.Style(e, 'background-color',
         {
            duration: OfficeAddin.ANIM_LENGTH,
            transition: Fx.Transitions.linear
         });
         if (fadeOut)
         {
            fx.onComplete = new function()
            {
               OfficeAddin.hideStatusText.delay(OfficeAddin.STATUS_FADE);
            }
         }
         fx.start('#ffffcc', '#ffffff');
      }
   },
   
   hideStatusText: function()
   {
      var e = $("statusText");
      e.setStyle("opacity", "1");
      var fx = new Fx.Style(e, 'opacity',
      {
         duration: OfficeAddin.ANIM_LENGTH
      }).start('1', '0');
   },
   
   /* AJAX call to perform server-side actions */
   runAction: function(useTemplate, Action, Doc, Msg)
   {
      if (Msg != "" && !confirm(Msg))
      {
         return;
      }
   
      OfficeAddin.showStatusText("Running action...", "ajax_anim.gif", false);
      var actionURL = useTemplate + "?a=" + Action + "&d=" + Doc;
      var myAjax = new Ajax(actionURL, {
         method: 'get',
         headers: {'If-Modified-Since': 'Sat, 1 Jan 2000 00:00:00 GMT'},
         onComplete: function(textResponse, xmlResponse)
         {
            // Remove any trailing hash
            var href = window.location.href.replace("#", "")
            // Remove any previous "&st=" strings
            href = href.replace(/[?&]st=([^&$]+)/g, "");
            // Optionally add a status string
            if (textResponse != "")
            {
               href += "&st=" + encodeURI(textResponse);
            }
            window.location.href = href;
         }
      });
      myAjax.request();
   },

   /* Calculates and returns the context path for the current page */
   getContextPath: function()
   {
      var path = window.location.pathname;
      var idx = path.indexOf("/", 1);
      var contextPath = "";
      if (idx != -1)
      {
         contextPath = path.substring(0, idx);
      }
      else
      {
         contextPath = "";
      }
   
      return contextPath;
   }
};

window.addEvent('domready', OfficeAddin.init);