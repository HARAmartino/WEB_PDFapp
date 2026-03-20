package com.example.printedit.ui

// Common JS functions for Gizmodo/Web Edit features

// 1. Remove Ads (Generic + Gizmodo specific)
// 1. Remove Ads (Manual Trigger Only)
val removeAdsJs = """
window.peToggleRemoveAds = function(enable) {
    if (!enable) {
        console.log("PrintEdit: Stopping Ad Removal...");
        if (window._peAdObserver) {
            window._peAdObserver.disconnect();
            window._peAdObserver = null;
        }
        if (window._peAdInterval) {
            clearInterval(window._peAdInterval);
            window._peAdInterval = null;
        }
        // Remove the stylesheet
        var styleEl = document.getElementById('pe-ad-hide');
        if (styleEl) styleEl.remove();
        return;
    }
    
    console.log("PrintEdit: Persistently Removing Ads...");
    var selectors = [
        // Google
        '.google-auto-placed',
        'iframe[id^="google_ads_iframe"]',
        '.adsbygoogle', 'ins.adsbygoogle', 'ins[data-ad-client]',
        '[id^="div-gpt-ad"]',
        'div[data-google-query-id]',
        // Generic (hyphen-separated)
        '.nt-03', '.mod-ad', '.im-ad-unit',
        '.ad-slot', '.ad-banner', '.ad-container', '.ad-wrapper', '.ad-box', '.ad-label',
        '[class*="advertisement"]', '[class*="sponsored"]',
        '[class^="ad-"]', '[class*="-ad-"]',
        '[id^="ad-"]', '[id*="-ad-"]',
        '[class~="ads"]', '[class~="ad"]',
        '[id$="-ads"]', '[class$="-ads"]', '[class*="_ads_"]',
        'div[data-adname]',
        // Generic (underscore-separated)
        '[class*="ad_box"]', '[class*="ad_area"]', '[class*="ad_unit"]',
        '[class*="ad_wrap"]', '[class*="ad_space"]', '[class*="ad_block"]',
        '[id*="ad_box"]', '[id*="ad_area"]', '[id*="ad_unit"]',
        '[id*="ad_wrap"]', '[id*="ad_space"]',
        // Overlay / Interstitial
        '[class*="overlay-ad"]', '[class*="interstitial"]', '[id*="interstitial"]',
        '[class*="popup-ad"]', '[class*="modal-ad"]',
        // Attribute-based
        '[data-ad]', '[data-ads]', '[data-ad-unit]', '[data-ad-slot]',
        '[data-advertisement]', '[data-sponsor]', '[data-sponsored]',
        // AMP ads
        'amp-ad', 'amp-embed', 'amp-sticky-ad',
        // Video ads (selector-based)
        'video[autoplay][muted]:not([controls])',
        '[id^="vpaid"]', '[class*="video-ad"]', '[id*="video-ad"]',
        '[class*="video_ad"]', '[id*="video_ad"]',
        '[class*="videoAd"]', '[id*="videoAd"]',
        '.vjs-ad-playing', '.vjs-ad-loading', '.vjs-ad-container',
        '.jw-ad-container', '.jw-plugin-googima', '[class*="jwplayer"][class*="ad"]',
        '.mw-ad', '[class*="medianet"]',
        // Connatix / Vidazoo / SpringServe video ad players
        'div.connatix', '[id^="connatix"]', '[class*="connatix"]',
        '[id^="vidazoo"]', '[class*="vidazoo"]',
        '[id^="springserve"]', '[class*="springserve"]',
        '[id^="instream"]', '[class*="instream-ad"]',
        // Teads / Outbrain / Taboola
        '[id^="teads"]', '[class^="teads"]',
        '[id^="taboola-"]', '#taboola-below-article-thumbnails',
        '[id^="outbrain_widget"]', '[class*="outbrain"]',
        // iframe ad sources
        'iframe[src*="doubleclick.net"]',
        'iframe[src*="googlesyndication.com"]',
        'iframe[src*="criteo.com"]',
        'iframe[src*="outbrain.com"]',
        'iframe[src*="taboola.com"]',
        'iframe[src*="amazon-adsystem.com"]',
        'iframe[src*="nend.net"]',
        'iframe[src*="zucks.net"]',
        'iframe[src*="imobile.co.jp"]',
        'iframe[src*="geniee"]',
        'iframe[src*="fluct.jp"]',
        'iframe[src*="microad"]',
        'iframe[src*="connatix.com"]',
        'iframe[src*="vidazoo.com"]',
        'iframe[src*="spotxchange.com"]',
        'iframe[src*="springserve.com"]',
        // Japanese ad networks
        '[id^="popin_"]',
        '[id^="microad_"]', '[class*="microad"]', '[id*="microad"]',
        '[id^="fluct_"]',   '[class*="fluct_"]',  '[id*="fluct"]',
        '[class*="nend"]',  '[id*="nend"]',
        '[class*="zucks"]', '[id*="zucks"]',
        '[class*="i-mobile"]', '[id*="i-mobile"]',
        '[class*="imobile"]',  '[id*="imobile"]',
        '[class*="geniee"]',   '[id*="geniee"]',
        '[class*="adstir"]',   '[id*="adstir"]',
        '[class*="admatrix"]', '[id*="admatrix"]',
        '[class*="yj-ad"]',    '[id*="yj-ad"]',   '[id^="yj-ad"]',
        '[class*="nobnag"]',   '[id*="nobnag"]',
        '[class*="logly"]',    '[id*="logly"]',
        '[id^="yvpub"]',
        // SmartNews / Criteo / AppNexus
        '[class*="smartnews-ad"]',
        '[class*="criteo"]', '[id*="criteo"]',
        '[class*="appnexus"]',
        '[class*="prebid"]',
        '[class*="connatix"]',
        // Japanese affiliate networks
        '[class*="a8net"]', '[id*="a8net"]',
        '[class*="valuecommerce"]', '[id*="valuecommerce"]',
        '[class*="accesstrade"]', '[id*="accesstrade"]',
        '[class*="rentracks"]', '[id*="rentracks"]'
    ];

    // Build CSS rule for all static selectors — uses stylesheet so ad scripts
    // cannot override via element.style
    var AD_STYLE_ID = 'pe-ad-hide';

    function ensureStylesheet() {
        var style = document.getElementById(AD_STYLE_ID);
        if (!style) {
            style = document.createElement('style');
            style.id = AD_STYLE_ID;
            (document.head || document.documentElement).appendChild(style);
        }
        return style;
    }

    // Add a selector to the pe-ad-hide stylesheet (avoids duplicates)
    var _addedSelectors = window._peAdHiddenSelectors || {};
    window._peAdHiddenSelectors = _addedSelectors;

    function addCssHideRule(sel) {
        if (_addedSelectors[sel]) return;
        _addedSelectors[sel] = true;
        var style = ensureStylesheet();
        style.textContent += sel + '{display:none!important;height:0!important;max-height:0!important;margin:0!important;padding:0!important;overflow:hidden!important;}\n';
    }

    // Apply all static selectors via CSS at once
    function applyStaticRules() {
        var style = ensureStylesheet();
        var rules = '';
        selectors.forEach(function(sel) {
            if (!_addedSelectors[sel]) {
                _addedSelectors[sel] = true;
                rules += sel + '{display:none!important;height:0!important;max-height:0!important;margin:0!important;padding:0!important;overflow:hidden!important;}\n';
            }
        });
        if (rules) style.textContent += rules;
    }

    // Stop video playback inside an element
    function pauseVideos(el) {
        el.querySelectorAll('video').forEach(function(v) {
            try { v.pause(); v.src = ''; v.load(); } catch(e) {}
        });
        if (el.tagName === 'VIDEO') {
            try { el.pause(); el.src = ''; el.load(); } catch(e) {}
        }
    }

    // Mark an element with a data attribute so CSS hides it (more resilient than inline style)
    function markAdElement(el) {
        if (el.getAttribute('data-pe-ad-hidden')) return;
        el.setAttribute('data-pe-ad-hidden', '1');
        pauseVideos(el);
    }

    // Ensure the data-attribute CSS rule exists
    function ensureDataAttrRule() {
        addCssHideRule('[data-pe-ad-hidden]');
    }

    function clean() {
        ensureDataAttrRule();

        // Mark elements matched by selectors
        selectors.forEach(function(sel) {
            try {
                document.querySelectorAll(sel).forEach(function(el) {
                    markAdElement(el);
                    // Hide parent wrappers that are left empty or just contain ad labels
                    var parent = el.parentElement;
                    var depth = 0;
                    while (parent && parent !== document.body && depth < 5) {
                        var text = parent.textContent.replace(/Advertisement|広告|PR|Sponsored|スポンサーリンク/g, '').trim();
                        if (text === '') {
                            markAdElement(parent);
                            parent = parent.parentElement;
                            depth++;
                        } else {
                            break;
                        }
                    }
                });
            } catch(e) {}
        });

        // Floating corner video ads (position:fixed, small-ish, contains video)
        var vw = window.innerWidth;
        var vh = window.innerHeight;
        document.querySelectorAll('div, section, aside, figure').forEach(function(el) {
            if (el.getAttribute('data-pe-ad-hidden')) return;
            var cs = window.getComputedStyle(el);
            if (cs.position !== 'fixed' && cs.position !== 'absolute') return;
            var zi = parseInt(cs.zIndex) || 0;
            if (zi < 100) return;
            var rect = el.getBoundingClientRect();
            if (rect.width <= 0 || rect.height <= 0) return;
            var isCornerPlayer = rect.width <= vw * 0.6 && rect.height <= vh * 0.4 &&
                (rect.right >= vw * 0.5 || rect.left <= vw * 0.5);
            var hasVideo = el.querySelector('video') !== null;
            if (isCornerPlayer && hasVideo) {
                var inner = el.innerHTML;
                if (inner.indexOf('ad') !== -1 || inner.indexOf('Ad') !== -1 ||
                    inner.indexOf('doubleclick') !== -1 || inner.indexOf('googlesyndication') !== -1 ||
                    inner.indexOf('connatix') !== -1 || inner.indexOf('vidazoo') !== -1 ||
                    el.querySelector('[class*="ad"]') || el.querySelector('[id*="ad"]')) {
                    markAdElement(el);
                }
            }
        });

        // Fixed/sticky bottom banners
        document.querySelectorAll('div, section, aside').forEach(function(el) {
            if (el.getAttribute('data-pe-ad-hidden')) return;
            var cs = window.getComputedStyle(el);
            if (cs.position !== 'fixed' && cs.position !== 'sticky') return;
            var bottom = parseFloat(cs.bottom);
            if (isNaN(bottom) || bottom > 120) return;
            var rect = el.getBoundingClientRect();
            if (rect.height <= 0 || rect.height > 300) return;
            if (rect.width < vw * 0.35) return;
            if (el.querySelector('iframe') ||
                el.querySelector('video[autoplay]') ||
                el.querySelector('[class*="ad"]') ||
                el.querySelector('[id*="ad"]') ||
                el.querySelector('ins') ||
                el.innerHTML.indexOf('doubleclick') !== -1 ||
                el.innerHTML.indexOf('googlesyndication') !== -1) {
                markAdElement(el);
            }
        });

        // Full-screen / large overlay ads
        document.querySelectorAll('div, section').forEach(function(el) {
            if (el.getAttribute('data-pe-ad-hidden')) return;
            var cs = window.getComputedStyle(el);
            if (cs.position !== 'fixed') return;
            var rect = el.getBoundingClientRect();
            if (rect.width < vw * 0.7 || rect.height < vh * 0.3) return;
            var zi = parseInt(cs.zIndex) || 0;
            if (zi < 1000) return;
            var hasAdContent = el.querySelector('video[autoplay]') ||
                el.querySelector('iframe[src*="ad"]') ||
                el.querySelector('.adsbygoogle') ||
                el.innerHTML.indexOf('doubleclick') !== -1;
            if (hasAdContent) markAdElement(el);
        });

        // Hide standalone ad labels
        var exactTexts = ['Advertisement', '広告', 'PR', 'Sponsored', 'スポンサーリンク'];
        ['span', 'div', 'p', 'h6', 'small', 'aside'].forEach(function(tag) {
            document.querySelectorAll(tag).forEach(function(el) {
                if (el.getAttribute('data-pe-ad-hidden')) return;
                if (el.childElementCount <= 1) {
                    var text = el.textContent.trim();
                    if (exactTexts.indexOf(text) !== -1) {
                        markAdElement(el);
                        var p = el.parentElement;
                        if (p && p.textContent.trim() === text) {
                            markAdElement(p);
                        }
                    }
                }
            });
        });

        // Native ads with "広告" disclosure badge
        document.querySelectorAll('span, div, p, small, section, aside, li').forEach(function(label) {
            if (label.getAttribute('data-pe-ad-hidden')) return;
            var t = label.textContent.trim();
            if (!t.startsWith('広告') || t.length > 12) return;
            var candidate = label.parentElement;
            var depth = 0;
            while (candidate && candidate !== document.body && depth < 8) {
                if (candidate.getAttribute('data-pe-ad-hidden')) break;
                var rect = candidate.getBoundingClientRect();
                if (rect.width >= vw * 0.6 && rect.height >= 80) {
                    markAdElement(candidate);
                    break;
                }
                candidate = candidate.parentElement;
                depth++;
            }
        });
    }

    // Override HTMLVideoElement.prototype.play to intercept ad video playback
    if (!window._peVideoPlayOverridden) {
        window._peVideoPlayOverridden = true;
        var origPlay = HTMLVideoElement.prototype.play;
        HTMLVideoElement.prototype.play = function() {
            var el = this;
            var parent = el.parentElement;
            var depth = 0;
            while (parent && parent !== document.body && depth < 6) {
                var cls = (parent.className || '').toLowerCase();
                var id  = (parent.id || '').toLowerCase();
                if (cls.indexOf('ad') !== -1 || id.indexOf('ad') !== -1 ||
                    cls.indexOf('connatix') !== -1 || cls.indexOf('vidazoo') !== -1 ||
                    cls.indexOf('vjs-ad') !== -1 || cls.indexOf('jw-ad') !== -1 ||
                    id.indexOf('connatix') !== -1 || id.indexOf('vidazoo') !== -1) {
                    el.muted = true;
                    el.setAttribute('data-pe-ad-hidden', '1');
                    return Promise.resolve();
                }
                parent = parent.parentElement;
                depth++;
            }
            return origPlay.apply(el, arguments);
        };
    }

    // Apply CSS rules for all static selectors
    applyStaticRules();

    // Run heuristic clean immediately
    clean();

    // Run persistently (MutationObserver) — DEBOUNCED to prevent flickering
    if (!window._peAdObserver) {
        var _peCleanTimer = null;
        var _peIsCleaning = false;
        window._peAdObserver = new MutationObserver(function(mutations) {
            // Skip if we are the ones making changes
            if (_peIsCleaning) return;
            // Debounce: batch rapid DOM changes into a single clean() call
            if (_peCleanTimer) clearTimeout(_peCleanTimer);
            _peCleanTimer = setTimeout(function() {
                _peIsCleaning = true;
                clean();
                _peIsCleaning = false;
            }, 200);
        });
        window._peAdObserver.observe(document.body, { childList: true, subtree: true });
    }

    // Reduced interval (6 runs × 10s = 60s total) for stubborn ads that get dynamically re-injected
    if (!window._peAdInterval) {
        var cleanCount = 0;
        window._peAdInterval = setInterval(function() {
            clean();
            cleanCount++;
            if (cleanCount >= 6) {
                clearInterval(window._peAdInterval);
                window._peAdInterval = null;
            }
        }, 10000);
    }
};
""".trimIndent()

// 2. Text Only Mode
val toggleTextOnlyJs = """
window.toggleTextOnly = function(enable) {
    var STYLE_ID = 'pe-text-only-style';
    var style = document.getElementById(STYLE_ID);
    if (enable) {
        if (!style) {
            style = document.createElement('style');
            style.id = STYLE_ID;
            style.innerHTML =
                'img, video, iframe, svg, canvas { display: none !important; }' +
                'div, section, article, p, span, h1, h2, h3, h4, h5, h6, li, a { background: white !important; color: black !important; }' +
                'body { background-color: white !important; color: black !important; font-family: sans-serif !important; }';
            (document.head || document.documentElement).appendChild(style);
        }
    } else {
        if (style) style.remove();
    }
};
""".trimIndent()

// 3. Grayscale Mode
val toggleGrayscaleJs = """
window.toggleGrayscale = function(enable) {
    var STYLE_ID = 'pe-grayscale-style';
    var style = document.getElementById(STYLE_ID);
    if (enable) {
        if (!style) {
            style = document.createElement('style');
            style.id = STYLE_ID;
            style.innerHTML = 'html { filter: grayscale(100%) !important; }';
            (document.head || document.documentElement).appendChild(style);
        }
    } else {
        if (style) style.remove();
    }
};
""".trimIndent()

// 4. Remove Elements (Tap to remove mode with "delete below" option)
// Uses event capture on click instead of overlay so scrolling still works
// Auto-walks up to block-level parent so user doesn't select tiny <span>s
val toggleRemoveElementModeJs = """
(function() {
    if (window._peRemoveElementInitialized) return;
    window._peRemoveElementInitialized = true;
    
    // Track removed selectors for presets
    window._peRemovedSelectors = window._peRemovedSelectors || [];
    
    function getCssSelector(el) {
        if (!(el instanceof Element)) return null;
        var path = [];
        var currentNode = el;
        while (currentNode.nodeType === Node.ELEMENT_NODE) {
            var selector = currentNode.nodeName.toLowerCase();
            if (currentNode.id) {
                // Escape special characters in ID
                selector += '#' + currentNode.id.replace(/(:|\.|\[|\]|,|=|@|\/|\\)/g, '\\\\$1');
                path.unshift(selector);
                break; // IDs are unique, can stop here
            } else {
                var sib = currentNode, nth = 1;
                while (sib = sib.previousElementSibling) {
                    if (sib.nodeName.toLowerCase() == selector) nth++;
                }
                if (nth != 1) selector += ":nth-of-type("+nth+")";
            }
            path.unshift(selector);
            currentNode = currentNode.parentNode;
        }
        return path.join(" > ");
    }

    window.peGetRemovedSelectors = function() {
        return JSON.stringify(window._peRemovedSelectors);
    };

    window.peApplyRemovedSelectors = function(input) {
        try {
            var selectors = (typeof input === 'string') ? JSON.parse(input) : input;
            selectors.forEach(function(sel) {
                if (window._peRemovedSelectors.indexOf(sel) === -1) {
                    window._peRemovedSelectors.push(sel);
                }
                document.querySelectorAll(sel).forEach(function(e) {
                    e.style.display = 'none';
                    e.style.height = '0';
                    e.style.maxHeight = '0';
                    e.style.margin = '0';
                    e.style.padding = '0';
                    e.style.overflow = 'hidden';
                });
            });
        } catch(e) { console.error("Error applying selectors", e); }
    };
    
    // Undo Stack handling
    window._peUndoStack = window._peUndoStack || [];
    
    window.peUndoLastAction = function() {
        if (!window._peUndoStack || window._peUndoStack.length === 0) return;
        var lastAction = window._peUndoStack.pop();
        lastAction.forEach(function(item) {
            if (item.element) {
                item.element.style.display = item.display;
                item.element.style.height = item.height;
                item.element.style.maxHeight = item.maxHeight;
                item.element.style.margin = item.margin;
                item.element.style.padding = item.padding;
                item.element.style.overflow = item.overflow;
            }
            if (item.selector) {
                var idx = window._peRemovedSelectors.indexOf(item.selector);
                if (idx !== -1) window._peRemovedSelectors.splice(idx, 1);
            }
        });
    };
    
    var STYLE_ID = 'pe-remove-element-styles';
    var lastHighlighted = null;
    var isActive = false;
    
    var touchStartX = 0;
    var touchStartY = 0;
    var isScrolling = false;
    
    // Inline elements that are too small to be a meaningful selection target
    var INLINE_TAGS = ['SPAN','EM','STRONG','B','I','U','A','SMALL','BIG','SUB','SUP','MARK','ABBR','CITE','CODE','TIME','LABEL','BR','WBR','IMG'];
    var BLOCK_TAGS = ['DIV','P','SECTION','ARTICLE','FIGURE','LI','UL','OL','TABLE','TR','BLOCKQUOTE','H1','H2','H3','H4','H5','H6','HEADER','FOOTER','NAV','ASIDE','MAIN','DETAILS','SUMMARY','FORM','FIELDSET','PRE'];
    
    if (!document.getElementById(STYLE_ID)) {
        var style = document.createElement('style');
        style.id = STYLE_ID;
        style.innerHTML =
            '.pe-remove-highlight { outline: 3px solid #f44336 !important; background: rgba(244, 67, 54, 0.15) !important; }' +
            '.pe-remove-active { cursor: crosshair !important; }' +
            '.pe-remove-confirm { position:fixed; bottom:100px; left:50%; transform:translateX(-50%); z-index:2147483647; background:#fff; border-radius:12px; box-shadow:0 4px 20px rgba(0,0,0,0.3); padding:12px 8px; display:flex; gap:8px; }' +
            '.pe-remove-btn { padding:10px 16px; border:none; border-radius:8px; font-size:14px; font-weight:bold; cursor:pointer; white-space:nowrap; }' +
            '.pe-remove-btn-single { background:#f44336; color:white; }' +
            '.pe-remove-btn-below { background:#ff9800; color:white; }' +
            '.pe-remove-btn-cancel { background:#9e9e9e; color:white; }';
        (document.head || document.documentElement).appendChild(style);
    }
    
    var confirmBar = null;
    
    // Walk up from tiny inline elements to the nearest meaningful block parent
    function findMeaningfulElement(el) {
        var current = el;
        var maxWalk = 5; // Don't walk too far up
        while (current && current !== document.body && current !== document.documentElement && maxWalk > 0) {
            if (BLOCK_TAGS.indexOf(current.tagName) !== -1) return current;
            // If it's a non-inline element with substantial size, it's probably meaningful
            if (INLINE_TAGS.indexOf(current.tagName) === -1) {
                var rect = current.getBoundingClientRect();
                if (rect.width > 100 && rect.height > 30) return current;
            }
            current = current.parentElement;
            maxWalk--;
        }
        return el; // Fallback to original if nothing better found
    }
    
    function removeElement(el, actionState) {
        if (!el) return;
        var sel = getCssSelector(el);
        // Save state for undo
        actionState.push({
            element: el,
            selector: sel,
            display: el.style.display,
            height: el.style.height,
            maxHeight: el.style.maxHeight,
            margin: el.style.margin,
            padding: el.style.padding,
            overflow: el.style.overflow
        });
        
        if (sel && window._peRemovedSelectors.indexOf(sel) === -1) {
            window._peRemovedSelectors.push(sel);
        }
        
        el.style.display = 'none';
        el.style.height = '0';
        el.style.maxHeight = '0';
        el.style.margin = '0';
        el.style.padding = '0';
        el.style.overflow = 'hidden';
    }
    
    function hideConfirmBar() {
        if (confirmBar) { confirmBar.remove(); confirmBar = null; }
    }
    
    function showConfirmBar(targetEl) {
        hideConfirmBar();
        confirmBar = document.createElement('div');
        confirmBar.className = 'pe-remove-confirm';
        
        var btnSingle = document.createElement('button');
        btnSingle.className = 'pe-remove-btn pe-remove-btn-single';
        btnSingle.textContent = 'この要素だけ';
        btnSingle.onclick = function(e) {
            e.stopPropagation();
            var actionState = [];
            removeElement(targetEl, actionState);
            window._peUndoStack.push(actionState);
            
            targetEl.classList.remove('pe-remove-highlight');
            lastHighlighted = null;
            hideConfirmBar();
        };
        
        var btnBelow = document.createElement('button');
        btnBelow.className = 'pe-remove-btn pe-remove-btn-below';
        btnBelow.textContent = 'ここから下を全て';
        btnBelow.onclick = function(e) {
            e.stopPropagation();
            var actionState = [];
            var el = targetEl;
            
            while (el && el !== document.body && el !== document.documentElement) {
                var next = el.nextElementSibling;
                while (next) {
                    var toRemove = next;
                    next = next.nextElementSibling;
                    removeElement(toRemove, actionState);
                }
                el = el.parentElement;
            }
            removeElement(targetEl, actionState);
            window._peUndoStack.push(actionState);
            
            targetEl.classList.remove('pe-remove-highlight');
            lastHighlighted = null;
            hideConfirmBar();
        };
        
        var btnCancel = document.createElement('button');
        btnCancel.className = 'pe-remove-btn pe-remove-btn-cancel';
        btnCancel.textContent = '取消';
        btnCancel.onclick = function(e) {
            e.stopPropagation();
            if (lastHighlighted) lastHighlighted.classList.remove('pe-remove-highlight');
            lastHighlighted = null;
            hideConfirmBar();
        };
        
        confirmBar.appendChild(btnSingle);
        confirmBar.appendChild(btnBelow);
        confirmBar.appendChild(btnCancel);
        document.body.appendChild(confirmBar);
    }
    
    // Listen for touches to detect scrolling vs tapping
    document.addEventListener('touchstart', function(e) {
        if (!isActive) return;
        if (e.touches && e.touches.length > 0) {
            touchStartX = e.touches[0].clientX;
            touchStartY = e.touches[0].clientY;
        }
        isScrolling = false;
    }, { capture: true, passive: true });
    
    document.addEventListener('touchmove', function(e) {
        if (!isActive) return;
        if (e.touches && e.touches.length > 0) {
            var dx = Math.abs(e.touches[0].clientX - touchStartX);
            var dy = Math.abs(e.touches[0].clientY - touchStartY);
            if (dx > 10 || dy > 10) {
                isScrolling = true;
            }
        }
    }, { capture: true, passive: true });
    
    // Click handler on capture phase - intercepts all clicks when active
    function onCaptureClick(e) {
        if (!isActive) return;
        
        var rawEl = e.target;
        
        // Don't intercept clicks on the confirm bar
        if (confirmBar && confirmBar.contains(rawEl)) return;
        
        // Always prevent default clicks so links don't open
        e.preventDefault();
        e.stopPropagation();
        
        // If the user was scrolling, don't trigger the highlight
        if (isScrolling) return;
        
        // Walk up to a meaningful block-level element
        var el = findMeaningfulElement(rawEl);
        
        if (el && el !== document.body && el !== document.documentElement) {
            if (lastHighlighted) lastHighlighted.classList.remove('pe-remove-highlight');
            lastHighlighted = el;
            el.classList.add('pe-remove-highlight');
            showConfirmBar(el);
        }
    }
    
    document.addEventListener('click', onCaptureClick, true);
    
    window.toggleRemoveElementMode = function(enable) {
        isActive = enable;
        if (enable) {
            document.body.classList.add('pe-remove-active');
        } else {
            document.body.classList.remove('pe-remove-active');
            if (lastHighlighted) {
                lastHighlighted.classList.remove('pe-remove-highlight');
                lastHighlighted = null;
            }
            hideConfirmBar();
            // Note: We deliberately do NOT clear the undo stack here
            // so users can leave edit mode and come back, and still undo.
        }
    };
})();
""".trimIndent()

// 4b. Remove Related Articles / Footer sections
val removeRelatedArticlesJs = """
(function() {
    var selectors = [
        '[class*="related"]',
        '[class*="recommend"]',
        '[class*="popular"]',
        '[class*="ranking"]',
        '[class*="sidebar"]',
        '[class*="widget"]',
        '[id*="related"]',
        '[id*="recommend"]',
        '[id*="popular"]',
        '[id*="ranking"]',
        'aside',
        'footer',
        'nav:not(:first-of-type)',
        '[class*="more-from"]',
        '[class*="more_from"]',
        '[class*="also-on"]',
        '[class*="read-more"]',
        '[class*="read_more"]',
        '[class*="next-article"]',
        '[class*="post-navigation"]',
        '[class*="comments"]',
        '[id*="comments"]',
        '[class*="share"]',
        '[class*="social"]',
        '[class*="sns"]',
        '[class*="footer"]',
        '[id*="footer"]'
    ];
    
    selectors.forEach(function(sel) {
        document.querySelectorAll(sel).forEach(function(el) {
            el.style.display = 'none';
            el.style.height = '0';
            el.style.maxHeight = '0';
            el.style.margin = '0';
            el.style.padding = '0';
            el.style.overflow = 'hidden';
        });
    });
})();
""".trimIndent()


// 6. Marquee Selection (Drag-to-Select)
val marqueeSelectionJs = """
(function() {
    if (window._peMarqueeInitialized) return;
    window._peMarqueeInitialized = true;
    
    // Config
    var SELECTED_CLASS = 'pe-link-selected';
    var STYLE_ID = 'pe-marquee-styles';
    
    // Inject Styles - removed touch-action: none so native scroll works!
    if (!document.getElementById(STYLE_ID)) {
        var style = document.createElement('style');
        style.id = STYLE_ID;
        style.innerHTML = 
            '.pe-marquee-overlay { position:fixed; top:0; left:0; width:100%; height:100%; z-index:2147483647; cursor:crosshair; touch-action: auto; background: transparent; }' +
            '.pe-selection-box { position:absolute; border: 2px solid #2196F3; background: rgba(33, 150, 243, 0.2); pointer-events:none; z-index:2147483647; }' +
            '.' + SELECTED_CLASS + ' { outline: 3px solid #ff0000 !important; background: rgba(255, 255, 0, 0.4) !important; box-shadow: 0 0 4px rgba(0,0,0,0.5); }';
        document.head.appendChild(style);
    }
    
    var overlay = null;
    var selectionBox = null;
    var startX = 0, startY = 0;
    var isSelecting = false;
    var autoScrollInterval = null;
    var lastTouchY = 0;
    
    // Long-press detection variables
    var longPressTimer = null;
    var touchStartX = 0;
    var touchStartY = 0;

    window.toggleMarqueeMode = function(enable) {
        if (enable) {
            clearSelection();
            if (!overlay) {
                overlay = document.createElement('div');
                overlay.className = 'pe-marquee-overlay';
                
                // Touch Events
                overlay.addEventListener('touchstart', onTouchStart, {passive: false});
                overlay.addEventListener('touchmove', onTouchMove, {passive: false});
                overlay.addEventListener('touchend', onTouchEnd, {passive: false});
                overlay.addEventListener('touchcancel', onTouchEnd, {passive: false});
                
                document.body.appendChild(overlay);
            }
            overlay.style.display = 'block';
            document.body.style.userSelect = 'none'; 
        } else {
            if (overlay) overlay.style.display = 'none';
            document.body.style.userSelect = '';
            clearSelection();
            stopAutoScroll();
            if (longPressTimer) clearTimeout(longPressTimer);
        }
    };
    
    window.getMarqueeSelection = function() {
        var links = document.querySelectorAll('.' + SELECTED_CLASS);
        var resultMap = {};
        
        links.forEach(function(link) {
            var href = link.href;
            if (!href || href.startsWith('javascript:') || href.startsWith('#') || href.startsWith('mailto:') || href.startsWith('tel:')) return;
            
            try { var parsed = new URL(href); } catch(e) { return; }
            
            var pathParts = parsed.pathname.split('/').filter(function(p) { return p.length > 0; });
            if (pathParts.length < 1) return;

            var text = link.innerText.trim();
            if (!text) {
                var img = link.querySelector('img');
                if (img && img.alt) text = img.alt.trim();
            }
            if (!text) text = "";
            
            if (!resultMap.hasOwnProperty(href) || resultMap[href].length < text.length) {
                resultMap[href] = text;
            }
        });
        
        var result = [];
        for (var key in resultMap) {
            var finalTitle = resultMap[key];
            if (!finalTitle || finalTitle.length === 0) {
                var identicalLinks = document.querySelectorAll('a[href="' + key + '"]');
                for (var lh=0; lh<identicalLinks.length; lh++) {
                    var t = identicalLinks[lh].innerText.trim();
                    if (!t && identicalLinks[lh].querySelector('img')) t = identicalLinks[lh].querySelector('img').alt;
                    if (t) { finalTitle = t; break; }
                }
            }
            if (!finalTitle || finalTitle.length === 0) finalTitle = key;
            result.push({url: key, text: finalTitle});
        }
        return JSON.stringify(result);
    };

    function clearSelection() {
        if (selectionBox) { selectionBox.remove(); selectionBox = null; }
        document.querySelectorAll('.' + SELECTED_CLASS).forEach(function(el) {
            el.classList.remove(SELECTED_CLASS);
        });
    }

    function onTouchStart(e) {
        if (e.touches && e.touches.length > 1) {
            // Multi-touch overrides everything
            if (longPressTimer) clearTimeout(longPressTimer);
            return; 
        }
        
        var point = getPoint(e);
        touchStartX = point.x;
        touchStartY = point.y;
        
        // Start long-press timer
        if (longPressTimer) clearTimeout(longPressTimer);
        longPressTimer = setTimeout(function() {
            // User held down long enough -> start selection
            isSelecting = true;
            clearSelection();
            
            startX = touchStartX;
            startY = touchStartY + window.scrollY;
            lastTouchY = touchStartY;
            
            if (!selectionBox) {
                selectionBox = document.createElement('div');
                selectionBox.className = 'pe-selection-box';
                document.body.appendChild(selectionBox);
            }
            updateBox(startX, startY, 0, 0);
            startAutoScroll();
        }, 300); // 300ms delay to distinguish from quick scroll
    }

    function onTouchMove(e) {
        var point = getPoint(e);
        
        // If not selecting yet, check if they moved too far (scroll intent)
        if (!isSelecting) {
            var dx = Math.abs(point.x - touchStartX);
            var dy = Math.abs(point.y - touchStartY);
            if (dx > 10 || dy > 10) {
                // Moved -> cancel long press
                if (longPressTimer) clearTimeout(longPressTimer);
            }
            return; // Let browser scroll handle it normally
        }
        
        // We ARE selecting -> prevent default to stop scrolling, and update box
        e.preventDefault(); 
        
        var currentX = point.x;
        var currentY = point.y + window.scrollY;
        lastTouchY = point.y;
        
        var x = Math.min(startX, currentX);
        var y = Math.min(startY, currentY);
        var width = Math.abs(currentX - startX);
        var height = Math.abs(currentY - startY);
        
        updateBox(x, y, width, height);
    }

    function onTouchEnd(e) {
        if (longPressTimer) clearTimeout(longPressTimer);
        stopAutoScroll();
        
        if (!isSelecting) return;
        
        isSelecting = false;
        selectLinksInBox();
        if (selectionBox) { selectionBox.remove(); selectionBox = null; }
    }
    
    function startAutoScroll() {
        if (autoScrollInterval) return;
        autoScrollInterval = setInterval(function() {
            if (!isSelecting) return;
            
            var threshold = 80; // Distance from edge to trigger scroll
            var speed = 15;
            var didScroll = false;
            
            if (lastTouchY < threshold) {
                window.scrollBy(0, -speed);
                didScroll = true;
            } else if (lastTouchY > window.innerHeight - threshold) {
                window.scrollBy(0, speed);
                didScroll = true;
            }
            
            if (didScroll) {
                // Determine current X based on last drag position
                var currentX = parseInt(selectionBox.style.left) || startX;
                if (parseInt(selectionBox.style.width) > 0) {
                    currentX = (currentX === startX) ? startX + parseInt(selectionBox.style.width) : currentX;
                }
                
                var currentY = lastTouchY + window.scrollY;
                var x = Math.min(startX, currentX);
                var y = Math.min(startY, currentY);
                var width = Math.abs(currentX - startX);
                var height = Math.abs(currentY - startY);
                updateBox(x, y, width, height);
            }
        }, 16); // roughly 60fps
    }
    
    function stopAutoScroll() {
        if (autoScrollInterval) {
            clearInterval(autoScrollInterval);
            autoScrollInterval = null;
        }
    }
    
    // Define getPoint first, then wrap it to track lastTouchX
    function getPoint(e) {
        if (e.touches && e.touches.length > 0) {
            return {x: e.touches[0].clientX, y: e.touches[0].clientY};
        }
        return {x: e.clientX, y: e.clientY};
    }
    
    var lastTouchX = 0;
    var _originalGetPoint = getPoint;
    getPoint = function(e) {
        var p = _originalGetPoint(e);
        lastTouchX = p.x;
        return p;
    };
    
    function updateBox(x, y, w, h) {
        if (selectionBox) {
            selectionBox.style.left = x + 'px';
            selectionBox.style.position = 'absolute';
            selectionBox.style.top = y + 'px';
            selectionBox.style.width = w + 'px';
            selectionBox.style.height = h + 'px';
        }
    }
    
    function selectLinksInBox() {
        if (!selectionBox) return;
        var rect = selectionBox.getBoundingClientRect();
        var links = document.querySelectorAll('a');
        
        links.forEach(function(link) {
            var href = link.href;
            if (!href || href.startsWith('javascript:') || href.startsWith('#') || href.startsWith('mailto:') || href.startsWith('tel:')) return;
            try {
                new URL(href);
            } catch(e) {
                return; // 無効な URL はスキップ
            }

            // Ignore invisible wrappers
            var style = window.getComputedStyle(link);
            if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return;
            
            var intersect = false;
            
            function checkIntersect(rect1, rect2) {
                if (rect1.width === 0 || rect1.height === 0) return false;
                // Require at least a 2px intrusion to prevent accidental padding touches
                return !(rect1.right <= rect2.left + 2 || 
                         rect1.left >= rect2.right - 2 || 
                         rect1.bottom <= rect2.top + 2 || 
                         rect1.top >= rect2.bottom - 2);
            }
            
            // Check actual text/inline geometric boxes (highly precise) instead of bulky BoundingClientRect
            var linkRects = link.getClientRects();
            for (var r=0; r<linkRects.length; r++) {
                if (checkIntersect(linkRects[r], rect)) {
                    intersect = true;
                    break;
                }
            }
            
            if (!intersect && style.display === 'contents') {
                var children = link.getElementsByTagName('*');
                for (var i = 0; i < children.length; i++) {
                    var childRects = children[i].getClientRects();
                    for(var c = 0; c < childRects.length; c++) {
                        if (checkIntersect(childRects[c], rect)) {
                            intersect = true;
                            break;
                        }
                    }
                    if(intersect) break;
                }
            }
            
            if (intersect) {
                 link.classList.add(SELECTED_CLASS);
                 if (window.getComputedStyle(link).display === 'contents') {
                     var directChildren = link.children;
                     for (var j = 0; j < directChildren.length; j++) {
                         directChildren[j].classList.add(SELECTED_CLASS);
                     }
                 }
            }
        });
    }
})();
""".trimIndent()


// 5a. Force-load lazy images (data-src, loading="lazy", IntersectionObserver triggers)
val forceLoadLazyImagesJs = """
(function() {
    if (window._peForceLoadRunning) return;
    window._peForceLoadRunning = true;

    var lazyAttrs = [
        'data-src', 'data-lazy-src', 'data-original', 'data-lazy',
        'data-echo', 'data-url', 'data-img-src', 'data-image-src',
        'data-lazyload', 'data-source', 'data-pagespeed-lazy-src',
        'data-cfsrc', 'data-wp-src', 'data-hi-res-src',
        'data-full-src', 'data-actual-src', 'data-retina-src',
        'data-srcset', 'data-lazy-srcset', 'data-srcset-webp'
    ];

    // Check if a value looks like a valid image URL (absolute, protocol-relative, or relative path)
    function isValidSrc(val) {
        if (!val || val.length === 0) return false;
        // Reject data URIs for placeholder images (tiny base64 placeholders)
        if (val.indexOf('data:') === 0) {
            // Allow real data: images (> 200 chars are likely actual content)
            return val.length > 200;
        }
        // Reject javascript: and # anchors
        if (val.indexOf('javascript:') === 0 || val === '#') return false;
        // Accept absolute URLs, protocol-relative, absolute paths, and relative paths
        return true;
    }

    // Override IntersectionObserver.prototype.observe so future observations
    // immediately fire the callback with isIntersecting=true
    if (!window._peIOPatched) {
        window._peIOPatched = true;
        try {
            var _origObserve = IntersectionObserver.prototype.observe;
            IntersectionObserver.prototype.observe = function(target) {
                _origObserve.call(this, target);
                var self = this;
                // Find the callback: try __pe_cb first, then fall back to internal callback property
                var cb = self['__pe_cb'] || self['callback'] || null;
                // Some browsers store callback in a different internal slot; try takeRecords + manual trigger
                if (cb) {
                    requestAnimationFrame(function() {
                        try {
                            var rect = target.getBoundingClientRect();
                            cb([{
                                isIntersecting: true, intersectionRatio: 1,
                                target: target,
                                boundingClientRect: rect, intersectionRect: rect,
                                rootBounds: null, time: performance.now()
                            }], self);
                        } catch(e) {}
                    });
                } else {
                    // Fallback: manually disconnect and re-observe to force trigger
                    requestAnimationFrame(function() {
                        try {
                            target.style.visibility = target.style.visibility || '';
                            // Force a style recalculation to trigger observer
                            void target.offsetHeight;
                        } catch(e) {}
                    });
                }
            };
            // Intercept constructor to capture callback reference
            var _NativeIO = window.IntersectionObserver;
            window.IntersectionObserver = function(cb, opts) {
                var io = new _NativeIO(cb, opts);
                io['__pe_cb'] = cb;
                return io;
            };
            window.IntersectionObserver.prototype = _NativeIO.prototype;
        } catch(e) {}
    }

    // Extract images from <noscript> tags (some sites put real images inside noscript as JS-off fallback)
    function extractNoscriptImages() {
        document.querySelectorAll('noscript').forEach(function(ns) {
            var content = ns.textContent || ns.innerHTML || '';
            if (content.indexOf('<img') === -1) return;
            // Check if the previous sibling is an img placeholder
            var prevImg = ns.previousElementSibling;
            if (prevImg && prevImg.tagName === 'IMG') {
                // Parse the noscript content to extract src
                var m = content.match(/src=["']([^"']+)["']/);
                if (m && m[1] && isValidSrc(m[1])) {
                    if (!prevImg.src || prevImg.src.indexOf('data:') === 0 || prevImg.naturalWidth === 0) {
                        prevImg.src = m[1];
                    }
                }
            }
        });
    }

    function forceLoad() {
        // Promote data-* attributes to src/srcset
        document.querySelectorAll('img').forEach(function(img) {
            img.removeAttribute('loading');
            for (var i = 0; i < lazyAttrs.length; i++) {
                var attr = lazyAttrs[i];
                var val = img.getAttribute(attr);
                if (!val || !isValidSrc(val)) continue;
                if (attr.indexOf('srcset') !== -1) {
                    if (img.srcset !== val) img.srcset = val;
                } else {
                    if (img.src !== val) img.src = val;
                }
                break;
            }
            // Handle CSS background-image lazy loading (common pattern: img with no src, bg set by JS)
            if (!img.src || img.src === '' || img.src === window.location.href) {
                var bg = window.getComputedStyle(img).backgroundImage;
                if (bg && bg !== 'none') {
                    var m = bg.match(/url\(["']?([^"')]+)["']?\)/);
                    if (m && m[1] && isValidSrc(m[1])) {
                        img.src = m[1];
                    }
                }
            }
        });

        // Promote data-* on <source> inside <picture>
        document.querySelectorAll('picture source').forEach(function(source) {
            for (var i = 0; i < lazyAttrs.length; i++) {
                var val = source.getAttribute(lazyAttrs[i]);
                if (val && isValidSrc(val)) {
                    if (source.srcset !== val) source.srcset = val;
                    break;
                }
            }
        });

        // Extract noscript images
        extractNoscriptImages();

        // Trigger scroll/resize events to activate IntersectionObserver-based loaders
        // Use requestAnimationFrame to allow layout to complete between scroll events
        window.dispatchEvent(new Event('scroll', {bubbles: true}));
        window.dispatchEvent(new Event('resize'));

        // Trigger lazy loaders on scrollable containers (limit to reasonable count)
        var scrollables = document.querySelectorAll('[style*="overflow"], [class*="scroll"], main, article, .content, #content');
        scrollables.forEach(function(el) {
            if (el.scrollHeight > el.clientHeight + 50) {
                el.dispatchEvent(new Event('scroll', {bubbles: true}));
            }
        });
    }

    // Staggered force-load with increasing delays
    forceLoad();
    setTimeout(forceLoad, 500);
    setTimeout(forceLoad, 1500);
    setTimeout(forceLoad, 4000);
    setTimeout(forceLoad, 8000);
    setTimeout(forceLoad, 15000);

    // Watch for dynamically added images (debounced to avoid conflicts with ad removal)
    var _imgDebounceTimer = null;
    var _imgMutObs = new MutationObserver(function(mutations) {
        // Skip mutations from ad removal (pe-ad-hide style changes)
        var isAdChange = false;
        for (var i = 0; i < mutations.length; i++) {
            var t = mutations[i].target;
            if (t && t.id === 'pe-ad-hide') { isAdChange = true; break; }
        }
        if (isAdChange) return;
        // Debounce: batch rapid mutations into a single forceLoad call
        if (_imgDebounceTimer) clearTimeout(_imgDebounceTimer);
        _imgDebounceTimer = setTimeout(forceLoad, 300);
    });
    _imgMutObs.observe(document.body, {childList: true, subtree: true, attributes: true, attributeFilter: ['data-src', 'data-lazy-src', 'data-original', 'src']});
    setTimeout(function() { _imgMutObs.disconnect(); }, 20000);
})();
""".trimIndent()

// 5b. Image Adjustment (Fit to Screen)
val smartFitImagesJs = """
(function() {
    var images = document.querySelectorAll('img');
    images.forEach(function(img) {
        if (img.width > window.innerWidth) {
            img.style.maxWidth = '100%';
            img.style.height = 'auto';
        }
    });
})();
""".trimIndent()

// 6. Menu Fix code removed to use the updated menuFixJs at the bottom of the file

// 7. No Background Mode (Placeholder / Implementation)
val toggleNoBackgroundJs = """
window.toggleNoBackground = function(enable) {
    var STYLE_ID = 'pe-no-bg-style';
    var style = document.getElementById(STYLE_ID);
    if (enable) {
        if (!style) {
            style = document.createElement('style');
            style.id = STYLE_ID;
            style.innerHTML = '* { background: transparent !important; background-color: transparent !important; } body { background: white !important; }';
            (document.head || document.documentElement).appendChild(style);
        }
    } else {
        if (style) style.remove();
    }
};
""".trimIndent()

// 8. Menu Fix JS - Fixes semi-transparent menus, overflow clipping, and SPA menu resets
val menuFixJs = """
(function() {
    var STYLE_ID = 'pe-menu-fix-style';
    if (!document.getElementById(STYLE_ID)) {
        var style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent =
            'html, body { -webkit-overflow-scrolling: touch !important; }\n' +
            'header, nav, .navbar, [role="navigation"] { overflow: visible !important; }\n' +
            /* Force full opacity on common nav/menu/overlay patterns */
            'header, nav, [role="navigation"], [role="menu"], [role="menubar"],\n' +
            '[class*="nav-"], [class*="-nav"], [class*="menu-"], [class*="-menu"],\n' +
            '[class*="drawer"], [class*="sidebar"], [class*="header"],\n' +
            '[class*="gnav"], [class*="global-nav"], [class*="site-nav"] {\n' +
            '    opacity: 1 !important;\n' +
            '}';
        (document.head || document.documentElement).appendChild(style);
    }

    /* Advanced heuristic for transparent thick menus and overlays */
    function fixTransparentMenus() {
        try {
            var sel = 'header, nav, [role="navigation"], [role="menu"], [role="dialog"],' +
                '[class*="nav"], [class*="menu"], [class*="drawer"], [class*="sidebar"], ' +
                '[class*="gnav"], [class*="overlay"], [class*="modal"], [class*="popup"], [id*="menu"]';
            
            document.querySelectorAll(sel).forEach(function(el) {
                var cs = window.getComputedStyle(el);
                var bg = cs.backgroundColor;
                var bf = cs.backdropFilter || cs.webkitBackdropFilter;
                var hasBackdropBlur = (bf && bf !== 'none' && bf.indexOf('blur') !== -1);
                
                var isTranslucent = false;
                var r=255, g=255, b=255; // Default solid fallback
                
                if (bg && bg.indexOf('rgba') !== -1) {
                    var m = bg.match(/rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)/);
                    if (m) {
                        var alpha = parseFloat(m[4]);
                        r = m[1]; g = m[2]; b = m[3];
                        if ((alpha > 0.01 && alpha < 0.98) || (alpha === 0 && hasBackdropBlur)) {
                            isTranslucent = true;
                        }
                    }
                }
                
                if (hasBackdropBlur || isTranslucent) {
                    var pos = cs.position;
                    if (pos === 'fixed' || pos === 'absolute' || pos === 'sticky' || el.tagName.toLowerCase() === 'nav' || el.tagName.toLowerCase() === 'header') {
                        el.style.setProperty('background-color', 'rgb(' + r + ',' + g + ',' + b + ')', 'important');
                        el.style.setProperty('backdrop-filter', 'none', 'important');
                        el.style.setProperty('-webkit-backdrop-filter', 'none', 'important');
                    }
                }
            });
            
            // Second pass: Find ANY element covering the whole screen (width > 80vw, height > 80vh) that is position: fixed and translucent
            var ww = window.innerWidth;
            var wh = window.innerHeight;
            document.querySelectorAll('div, section, aside, form').forEach(function(el) {
                var cs = window.getComputedStyle(el);
                if (cs.position === 'fixed' || cs.position === 'absolute') {
                    if (el.offsetWidth > ww * 0.8 && el.offsetHeight > wh * 0.8) {
                        var bg = cs.backgroundColor;
                        var m = bg ? bg.match(/rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)/) : null;
                        var alpha = m ? parseFloat(m[4]) : 1;
                        var bf = cs.backdropFilter || cs.webkitBackdropFilter;
                        
                        if ((alpha > 0.05 && alpha < 0.98) || (bf && bf !== 'none')) {
                            var r = m ? m[1] : 255;
                            var g = m ? m[2] : 255;
                            var b = m ? m[3] : 255;
                            el.style.setProperty('background-color', 'rgb(' + r + ',' + g + ',' + b + ')', 'important');
                            el.style.setProperty('backdrop-filter', 'none', 'important');
                            el.style.setProperty('-webkit-backdrop-filter', 'none', 'important');
                        }
                    }
                }
            });
        } catch(e) {}
    }
    
    // Initial runs
    fixTransparentMenus();
    setTimeout(fixTransparentMenus, 500);
    setTimeout(fixTransparentMenus, 2000);

    /* 再発防止策 (Recurrent prevention): Watch for dynamic menus on SPAs */
    // 1. Observe clicks (often opens menus)
    document.addEventListener('click', function() {
        setTimeout(fixTransparentMenus, 50);
        setTimeout(fixTransparentMenus, 300);
    }, { passive: true });
    
    // 2. Observe DOM mutations for newly added overlays
    if (typeof MutationObserver !== 'undefined' && document.body) {
        var menuObserver = new MutationObserver(function(mutations) {
            var shouldFix = false;
            for (var i = 0; i < mutations.length; i++) {
                if (mutations[i].addedNodes.length > 0 || mutations[i].attributeName === 'class' || mutations[i].attributeName === 'style') {
                    shouldFix = true; break;
                }
            }
            if (shouldFix) {
                clearTimeout(window._peMenuFixTimer);
                window._peMenuFixTimer = setTimeout(fixTransparentMenus, 150);
            }
        });
        menuObserver.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style'] });
    }
})();
""".trimIndent()
