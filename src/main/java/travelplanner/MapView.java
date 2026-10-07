package travelplanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.concurrent.Worker;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Interactive map of the itinerary, shown on the result screen.
 *
 * A JavaFX WebView displays a small web page that uses Leaflet (an open-source map library,
 * bundled in src/main/resources/map so it works without a CDN). The background map comes from
 * CARTO (needs a free key, CARTO_API_KEY in config.properties, since August 2026) or, without a
 * key, from the standard OpenStreetMap tiles. Java talks to the page by calling JavaScript functions
 * (setTrip, showDay, setTheme) with WebEngine.executeScript().
 */
public class MapView extends StackPane {

    private static String tileKey = "";   // set once at startup from config.properties

    private final WebView webView = new WebView();
    private final WebEngine engine = webView.getEngine();
    private boolean pageReady = false;
    private String pendingTripJson;   // trip data that arrived before the page finished loading
    private int pendingDay = -1;

    /** CARTO key (empty = use OpenStreetMap tiles instead). */
    public static void setTileKey(String key) {
        tileKey = key == null ? "" : key.trim().replaceAll("[^A-Za-z0-9_\\-]", "");   // keep it URL-safe
    }

    public MapView() {
        getStyleClass().add("map-view");
        webView.setContextMenuEnabled(false);
        getChildren().add(webView);

        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                pageReady = true;
                engine.executeScript("setTheme(" + Theme.isDark() + ")");
                if (pendingTripJson != null) {
                    engine.executeScript("setTrip(" + pendingTripJson + ")");
                    engine.executeScript("showDay(" + pendingDay + ")");
                }
            }
        });
        // the map is often created before it has a size; once it gets one, zoom to the route again
        webView.widthProperty().addListener((obs, oldW, newW) -> {
            if (pageReady && pendingTripJson != null && oldW.doubleValue() < 50 && newW.doubleValue() >= 50) {
                engine.executeScript("showDay(" + pendingDay + ")");
            }
        });
        Theme.darkProperty().addListener((obs, was, dark) -> {
            if (pageReady) engine.executeScript("setTheme(" + dark + ")");
        });
        engine.loadContent(buildPage());
    }

    /** Sends all days and stops to the map (as JSON built with Gson, so names are escaped safely). */
    public void showItinerary(Itinerary itinerary) {
        JsonObject trip = new JsonObject();
        trip.addProperty("lat", itinerary.getDestinationLat());
        trip.addProperty("lon", itinerary.getDestinationLon());
        JsonArray days = new JsonArray();
        int index = 0;
        for (ArrayList<ItineraryItem> day : itinerary.getDays()) {
            JsonObject d = new JsonObject();
            d.addProperty("color", Theme.dayColor(index));
            d.addProperty("label", "Day " + (index + 1));
            JsonArray stops = new JsonArray();
            int number = 1;
            for (ItineraryItem item : day) {
                JsonObject s = new JsonObject();
                s.addProperty("name", item.getPlace().getName());
                s.addProperty("lat", item.getPlace().getLat());
                s.addProperty("lon", item.getPlace().getLon());
                s.addProperty("time", item.getStartTime() + " to " + item.getEndTime());
                s.addProperty("meal", item.isMeal());
                s.addProperty("label", item.getLabel());
                s.addProperty("number", item.isMeal() ? 0 : number++);
                stops.add(s);
            }
            d.add("stops", stops);
            days.add(d);
            index++;
        }
        trip.add("days", days);
        pendingTripJson = trip.toString();
        if (pageReady) {
            engine.executeScript("setTrip(" + pendingTripJson + ")");
            engine.executeScript("showDay(" + pendingDay + ")");
        }
    }

    /** -1 = all days, otherwise the 0-based day index. */
    public void showDay(int dayIndex) {
        pendingDay = dayIndex;
        if (pageReady && pendingTripJson != null) engine.executeScript("showDay(" + dayIndex + ")");
    }

    // ------------------------------------------------------------------ the web page

    private String buildPage() {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><style>"
                + readResource("/map/leaflet.css")
                + PAGE_CSS
                // L_DISABLE_3D: position map squares without 3D transforms. With Windows display
                // scaling (125%/150%) JavaFX's browser draws 3D-positioned squares at the wrong size
                // and place, which showed up as missing / overlapping squares.
                + "</style><script>L_DISABLE_3D=true;</script><script>" + readResource("/map/leaflet.js")
                + "</script></head>"
                + "<body><div id='map'></div><div id='offline'>Map tiles need an internet connection</div>"
                + "<script>var KEY='" + tileKey + "';" + PAGE_JS + "</script></body></html>";
    }

    private static String readResource(String path) {
        try (InputStream in = MapView.class.getResourceAsStream(path)) {
            if (in == null) return "";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static final String PAGE_CSS =
            "html,body,#map{margin:0;height:100%;width:100%;}"
            + "body{font-family:'IBM Plex Sans','Segoe UI',sans-serif;background:#E9EEF2;}"
            + "body.dark{background:#0E1626;}.leaflet-container{background:transparent;}"
            + ".pin{background:none;border:none;}"
            + ".pin div{width:24px;height:24px;border-radius:50%;color:#fff;font:600 12px 'IBM Plex Sans','Segoe UI',sans-serif;"
            + "display:flex;align-items:center;justify-content:center;border:2px solid #fff;box-shadow:0 1px 4px rgba(20,33,61,.45);}"
            + ".pin div.meal{width:14px;height:14px;margin:5px;background:#fff !important;border-width:3px;}"
            + ".leaflet-popup-content{font:13px 'IBM Plex Sans','Segoe UI',sans-serif;color:#14213D;margin:10px 12px;}"
            + ".leaflet-popup-content b{font-size:14px;}"
            + ".leaflet-popup-content span{color:#5B6578;}"
            + "#offline{display:none;position:absolute;left:12px;bottom:12px;z-index:1000;background:#14213D;color:#fff;"
            + "font:12px 'IBM Plex Sans',sans-serif;padding:6px 10px;border-radius:6px;}";

    private static final String PAGE_JS =
            // with a CARTO key: CARTO Voyager (light) / Dark Matter (dark); without: standard OSM tiles
            "var OSM='https://tile.openstreetmap.org/{z}/{x}/{y}.png';"
            + "var LIGHT=KEY?'https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png?key='+KEY:OSM;"
            + "var DARK=KEY?'https://basemaps.cartocdn.com/rastertiles/dark_all/{z}/{x}/{y}.png?key='+KEY:OSM;"
            // animations off: JavaFX's built-in browser sometimes never finishes Leaflet's fade-in,
            // which leaves map squares invisible (the "gaps")
            + "var map=L.map('map',{zoomControl:true,fadeAnimation:false,zoomAnimation:false,"
            + "markerZoomAnimation:false}).setView([20.6,78.9],5);"
            + "var tiles=L.tileLayer(LIGHT,{maxZoom:19,subdomains:'abcd',"
            + "attribution:KEY?'&copy; OpenStreetMap contributors &copy; CARTO':'&copy; OpenStreetMap contributors'}).addTo(map);"
            // a square that failed to download is retried (up to 3 times, a little later each time)
            + "var tileErrors=0;tiles.on('tileerror',function(e){var img=e.tile;var n=(img._retries||0)+1;"
            + "if(n<=3){img._retries=n;var src=img.src.replace(/[?&]retry=\\d+/,'');"
            + "setTimeout(function(){img.src=src+(src.indexOf('?')<0?'?':'&')+'retry='+n;},800*n);return;}"
            + "tileErrors++;if(tileErrors>6)document.getElementById('offline').style.display='block';});"
            + "var layer=L.layerGroup().addTo(map);var trip=null;"
            + "function esc(t){return String(t).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}"
            + "function setTheme(d){tiles.setUrl(d?DARK:LIGHT);document.body.className=d?'dark':'';}"
            + "function setTrip(t){trip=t;}"
            + "function showDay(n){if(!trip)return;map.invalidateSize();layer.clearLayers();var all=[];"
            + " trip.days.forEach(function(day,i){if(n>=0&&i!==n)return;var line=[];"
            + "  day.stops.forEach(function(s){var p=[s.lat,s.lon];line.push(p);all.push(p);"
            + "   var html=s.meal?'<div class=\"meal\" style=\"border-color:'+day.color+'\"></div>'"
            + "        :'<div style=\"background:'+day.color+'\">'+s.number+'</div>';"
            + "   var icon=L.divIcon({className:'pin',html:html,iconSize:[24,24],iconAnchor:[12,12]});"
            + "   L.marker(p,{icon:icon,title:s.name}).bindPopup('<b>'+esc(s.name)+'</b><br><span>'+esc(day.label)"
            + "     +', '+esc(s.time)+'<br>'+esc(s.label)+'</span>').addTo(layer);});"
            + "  if(line.length>1)L.polyline(line,{color:day.color,weight:4,opacity:.9}).addTo(layer);});"
            + " if(all.length>1)map.fitBounds(all,{padding:[48,48],maxZoom:15});"
            + " else if(all.length==1)map.setView(all[0],14);else map.setView([trip.lat,trip.lon],12);}";
}
