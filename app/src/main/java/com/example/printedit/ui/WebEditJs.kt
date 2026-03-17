package com.example.printedit.ui

// Common JS functions for Gizmodo/Web Edit features

// 1. Remove Ads (Generic + Gizmodo specific)
// 1. Remove Ads (Manual Trigger Only)
val removeAdsJs = """
window.peManualRemoveAds = function() {
    console.log("PrintEdit: Persistently Removing Ads...");
    var selectors = [
        '.google-auto-placed', 
        'iframe[id^="google_ads_iframe"]', 
        '.adsbygoogle',
        '[id^="div-gpt-ad"]',
        '.nt-03', 
        '.mod-ad',
        '#taboola-below-article-thumbnails',
        '.im-ad-unit',
        '.ad-slot',
        '.ad-banner',
        '.ad-container',
        '.ad-wrapper',
        '.ad-box',
        '.ad-label',
        '[class*="advertisement"]',
        '[class*="sponsored"]',
        '[class^="ad-"]',
        '[class*="-ad-"]',
        '[id^="ad-"]',
        '[id*="-ad-"]',
        '[class~="ads"]',          // Exact word match: class="top ads" (safe)
        '[class~="ad"]',           // Exact word match: class="banner ad" (safe)
        '[id$="-ads"]',            // Ends with -ads (safe)
        '[class$="-ads"]',         // Ends with -ads (safe)
        '[class*="_ads_"]',        // Underscore delimited (safe)
        'div[data-adname]',
        'div[data-google-query-id]',
        '[id^="teads"]',
        '[class^="teads"]',
        '[class*="video-ad"]',
        '[id*="video-ad"]',
        '[id^="vpaid"]',
        'div.connatix',
        '.vjs-ad-playing',
        'div[id^="yvpub"]',
        'div[id^="popin_"]',
        'div[id^="microad_"]',
        'div[id^="fluct_"]',
        '[id^="taboola-"]',
        '[id^="outbrain_widget"]',
        'iframe[src*="criteo.com"]',
        'iframe[src*="outbrain.com"]',
        'iframe[src*="taboola.com"]'
    ];
    
    function clean() {
        selectors.forEach(function(sel) {
            document.querySelectorAll(sel).forEach(function(el) {
                el.style.display = 'none';
                el.style.height = '0';
                el.style.maxHeight = '0';
                el.style.margin = '0';
                el.style.padding = '0';
                el.style.overflow = 'hidden';
                
                // Hide parent wrappers that are left empty or just contain ad labels
                var parent = el.parentElement;
                var depth = 0;
                while (parent && parent !== document.body && depth < 3) {
                    var text = parent.textContent.replace(/Advertisement|広告|PR|Sponsored|スポンサーリンク/g, '').trim();
                    if (text === '') {
                        parent.style.display = 'none';
                        parent.style.height = '0';
                        parent.style.maxHeight = '0';
                        parent.style.margin = '0';
                        parent.style.padding = '0';
                        parent.style.overflow = 'hidden';
                        parent = parent.parentElement;
                        depth++;
                    } else {
                        break;
                    }
                }
            });
        });
        
        // Hide standalone ad labels
        var exactTexts = ['Advertisement', '広告', 'PR', 'Sponsored', 'スポンサーリンク'];
        ['span', 'div', 'p', 'h6', 'small', 'aside'].forEach(function(tag) {
            document.querySelectorAll(tag).forEach(function(el) {
                if (el.childElementCount <= 1) { // allow max 1 empty child
                    var text = el.textContent.trim();
                    if (exactTexts.indexOf(text) !== -1) {
                        el.style.display = 'none';
                        el.style.height = '0';
                        el.style.margin = '0';
                        el.style.padding = '0';
                        var p = el.parentElement;
                        if (p && p.textContent.trim() === text) {
                            p.style.display = 'none';
                            p.style.height = '0';
                            p.style.margin = '0';
                            p.style.padding = '0';
                        }
                    }
                }
            });
        });
    }

    // Run immediately
    clean();

    // Run persistently (MutationObserver)
    if (!window._peAdObserver) {
        window._peAdObserver = new MutationObserver(function(mutations) {
            clean();
        });
        window._peAdObserver.observe(document.body, { childList: true, subtree: true });
    }
    
    // Also add interval-based re-cleaning for stubborn ads (only when manually triggered)
    if (!window._peAdInterval) {
        var cleanCount = 0;
        window._peAdInterval = setInterval(function() {
            clean();
            cleanCount++;
            if (cleanCount >= 15) {
                clearInterval(window._peAdInterval);
                window._peAdInterval = null;
            }
        }, 2000);
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
            document.head.appendChild(style);
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
            document.head.appendChild(style);
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

    window.peApplyRemovedSelectors = function(jsonStr) {
        try {
            var selectors = JSON.parse(jsonStr);
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
        document.head.appendChild(style);
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
                var parsed = new URL(href);
                var pathParts = parsed.pathname.split('/').filter(function(p) { return p.length > 0; });
                if (pathParts.length < 1) return;
            } catch(e) {}
            
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


// 5. Image Adjustment (Fit to Screen)
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
            document.head.appendChild(style);
        }
    } else {
        if (style) style.remove();
    }
};
""".trimIndent()

// 8. Menu Fix JS - Fixes sites where menus are clipped, without forcing them open permanently
val menuFixJs = """
(function() {
    console.log('PrintEdit: Applying safe menu fix...');
    var style = document.createElement('style');
    style.id = 'pe-menu-fix-style';
    style.innerHTML = 
        '/* Smooth scrolling for touch */' +
        'html, body { -webkit-overflow-scrolling: touch !important; }' +
        '/* Fix header clipping without forcing visibility */' +
        'header, nav, .navbar { overflow: visible !important; }';
    
    if (!document.getElementById('pe-menu-fix-style')) {
        document.head.appendChild(style);
    }
})();
""".trimIndent()
