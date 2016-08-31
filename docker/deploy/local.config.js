// My ORR local.config.js

// (required) main ORR page URL.
// This can start with '//' to dynamically accommodate http or https access.
appConfig.portal.mainPage  = "//example.net/ont/";

// (required) orr-ont endpoint URL. (No trailing slash.)
// This could be a full URL ("https://example.net/ont")
// or a path relative to the orr-portal host ("/ont")
appConfig.orront.rest  = "/ont";

appConfig.orront.sparqlEndpoint = "http://example.net/sparql";

// (optional) URL of image to show in the page header.
// By default, this will be MMI ORR's logo.
//appConfig.branding.logo = "http://example.net/my_orr_logo.png";

// (optional) string used for <head><title> in main pages.
// By default, this will be related with the MMI ORR.
appConfig.branding.title  = "My ORR";

// (optional) "Terms of Use" link.
// No default value.
//appConfig.branding.tou = "http://somewhere/mytermsofuse"

// (optional) "Contact us" link.
// No default value.
//appConfig.branding.contactUs: "http://somewhere/contactus"  // OR  "mailto:addr@example.net"


// (optional) recaptcha siteKey.
// If given, then the corresponding
// privateKey must be specified in `orront.conf`
//appConfig.recaptcha.siteKey = "the-public-key"


appConfig.firebase.url = "https://mmiorr.firebaseio.com";
