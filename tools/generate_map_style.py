# Generates app/src/main/assets/map/woozy_dark.json (OpenMapTiles schema).
# Usage: python3 tools/generate_map_style.py app/src/main/assets/map/woozy_dark.json
import json, sys
SRC = "openmaptiles"
def width(stops, base=1.5):
    e = ["interpolate", ["exponential", base], ["zoom"]]
    for z, w in stops: e += [z, w]
    return e
def cls(*names): return ["match", ["get", "class"], list(names), True, False]
not_tunnel = ["!=", ["get", "brunnel"], "tunnel"]
is_tunnel = ["==", ["get", "brunnel"], "tunnel"]
is_bridge = ["==", ["get", "brunnel"], "bridge"]
C = dict(bg="#0B0C0E", resid="#1B1D20", indus="#1E2024", wood="#0F1E17", park="#12261A", water="#223347",
         building="#25282C", bline="#16171A", minor="#50565E", service="#3E434A", sec="#5E646C",
         primary="#666C75", mw_case="#2E4054", mw="#A3AAB2", trunk="#8C939B", path="#34383E", rail="#3A3E44",
         casing="#0F1012", label="#C3C8CE", halo="#0B0C0E")
L = []
L.append({"id": "background", "type": "background", "paint": {"background-color": C["bg"]}})
L.append({"id": "landuse-residential", "type": "fill", "source": SRC, "source-layer": "landuse", "minzoom": 9,
          "filter": cls("residential", "suburb", "neighbourhood", "quarter"),
          "paint": {"fill-color": C["resid"], "fill-opacity": ["interpolate", ["linear"], ["zoom"], 9, 0.4, 12, 1]}})
L.append({"id": "landuse-other", "type": "fill", "source": SRC, "source-layer": "landuse", "minzoom": 11,
          "filter": cls("industrial", "commercial", "retail", "railway", "hospital", "school", "university", "college"),
          "paint": {"fill-color": C["indus"]}})
L.append({"id": "landcover-wood", "type": "fill", "source": SRC, "source-layer": "landcover",
          "filter": cls("wood", "forest"), "paint": {"fill-color": C["wood"]}})
L.append({"id": "landcover-grass", "type": "fill", "source": SRC, "source-layer": "landcover",
          "filter": cls("grass", "wetland"), "paint": {"fill-color": C["wood"], "fill-opacity": 0.3}})
L.append({"id": "park", "type": "fill", "source": SRC, "source-layer": "park", "paint": {"fill-color": C["park"]}})
L.append({"id": "landuse-park", "type": "fill", "source": SRC, "source-layer": "landuse",
          "filter": cls("park", "cemetery", "pitch", "playground", "garden", "stadium"), "paint": {"fill-color": C["park"]}})
L.append({"id": "water", "type": "fill", "source": SRC, "source-layer": "water",
          "filter": ["!=", ["get", "brunnel"], "tunnel"], "paint": {"fill-color": C["water"]}})
L.append({"id": "waterway", "type": "line", "source": SRC, "source-layer": "waterway", "minzoom": 11,
          "paint": {"line-color": C["water"], "line-width": width([(11, 0.5), (16, 3), (20, 8)])}})
L.append({"id": "aeroway", "type": "line", "source": SRC, "source-layer": "aeroway", "minzoom": 11,
          "filter": cls("runway", "taxiway"), "paint": {"line-color": "#2A2D31", "line-width": width([(11, 2), (17, 40)])}})
L.append({"id": "building", "type": "fill", "source": SRC, "source-layer": "building", "minzoom": 14,
          "paint": {"fill-color": C["building"], "fill-outline-color": C["bline"],
                    "fill-opacity": ["interpolate", ["linear"], ["zoom"], 14, 0, 15, 1]}})

def road_layers(prefix, extra_filter, opacity=1.0):
    out = []
    def f(*classes): return ["all", extra_filter, cls(*classes)]
    # casings (bottom)
    out.append({"id": f"{prefix}-minor-casing", "type": "line", "source": SRC, "source-layer": "transportation", "minzoom": 13,
                "filter": f("minor", "service"), "layout": {"line-cap": "round", "line-join": "round"},
                "paint": {"line-color": C["casing"], "line-opacity": opacity, "line-width": width([(13, 1.5), (16, 7), (20, 30)])}})
    out.append({"id": f"{prefix}-major-casing", "type": "line", "source": SRC, "source-layer": "transportation", "minzoom": 8,
                "filter": f("primary", "secondary", "tertiary"), "layout": {"line-cap": "round", "line-join": "round"},
                "paint": {"line-color": C["casing"], "line-opacity": opacity, "line-width": width([(8, 1), (12, 4), (16, 12), (20, 44)])}})
    out.append({"id": f"{prefix}-motorway-casing", "type": "line", "source": SRC, "source-layer": "transportation", "minzoom": 5,
                "filter": f("motorway", "trunk"), "layout": {"line-cap": "round", "line-join": "round"},
                "paint": {"line-color": C["mw_case"], "line-opacity": opacity, "line-width": width([(5, 1), (10, 4), (14, 11), (18, 34), (20, 60)])}})
    # fills
    out.append({"id": f"{prefix}-path", "type": "line", "source": SRC, "source-layer": "transportation", "minzoom": 15,
                "filter": f("path", "track"), "paint": {"line-color": C["path"], "line-opacity": opacity,
                "line-dasharray": [2, 1.5], "line-width": width([(15, 1), (20, 4)])}})
    out.append({"id": f"{prefix}-rail", "type": "line", "source": SRC, "source-layer": "transportation", "minzoom": 12,
                "filter": f("rail", "transit"), "paint": {"line-color": C["rail"], "line-opacity": opacity, "line-width": width([(12, 1), (18, 3)])}})
    out.append({"id": f"{prefix}-service", "type": "line", "source": SRC, "source-layer": "transportation", "minzoom": 14,
                "filter": f("service"), "layout": {"line-cap": "round", "line-join": "round"},
                "paint": {"line-color": C["service"], "line-opacity": opacity, "line-width": width([(14, 1), (16, 3.5), (20, 18)])}})
    out.append({"id": f"{prefix}-minor", "type": "line", "source": SRC, "source-layer": "transportation", "minzoom": 12,
                "filter": f("minor"), "layout": {"line-cap": "round", "line-join": "round"},
                "paint": {"line-color": C["minor"], "line-opacity": opacity, "line-width": width([(12, 0.6), (16, 5), (20, 26)])}})
    out.append({"id": f"{prefix}-secondary-tertiary", "type": "line", "source": SRC, "source-layer": "transportation", "minzoom": 9,
                "filter": f("secondary", "tertiary"), "layout": {"line-cap": "round", "line-join": "round"},
                "paint": {"line-color": C["sec"], "line-opacity": opacity, "line-width": width([(9, 0.6), (12, 2.4), (16, 9), (20, 38)])}})
    out.append({"id": f"{prefix}-primary", "type": "line", "source": SRC, "source-layer": "transportation", "minzoom": 7,
                "filter": f("primary"), "layout": {"line-cap": "round", "line-join": "round"},
                "paint": {"line-color": C["primary"], "line-opacity": opacity, "line-width": width([(7, 0.6), (12, 3), (16, 10), (20, 40)])}})
    out.append({"id": f"{prefix}-trunk", "type": "line", "source": SRC, "source-layer": "transportation", "minzoom": 5,
                "filter": f("trunk"), "layout": {"line-cap": "round", "line-join": "round"},
                "paint": {"line-color": C["trunk"], "line-opacity": opacity, "line-width": width([(5, 0.6), (10, 2), (14, 6), (18, 24), (20, 46)])}})
    out.append({"id": f"{prefix}-motorway", "type": "line", "source": SRC, "source-layer": "transportation", "minzoom": 5,
                "filter": f("motorway"), "layout": {"line-cap": "round", "line-join": "round"},
                "paint": {"line-color": C["mw"], "line-opacity": opacity, "line-width": width([(5, 0.6), (10, 2), (14, 6), (18, 24), (20, 46)])}})
    return out

L += road_layers("tunnel", is_tunnel, opacity=0.45)
L += road_layers("road", ["all", not_tunnel, ["!=", ["get", "brunnel"], "bridge"]])
L += road_layers("bridge", is_bridge)

# Invisible-but-queryable layer used by the app to read the current road name under the GPS position.
L.append({"id": "road-name-query", "type": "line", "source": SRC, "source-layer": "transportation_name", "minzoom": 12,
          "filter": ["!", cls("path", "track", "rail", "transit", "ferry")],
          "paint": {"line-color": C["bg"], "line-opacity": 0.01, "line-width": 18}})

for placement, geom in (("point", "Point"), ("line", "LineString")):
    L.append({"id": f"water-name-{placement}", "type": "symbol", "source": SRC, "source-layer": "water_name", "minzoom": 12,
              "filter": ["==", ["geometry-type"], geom],
              "layout": {"text-field": ["coalesce", ["get", "name"], ""], "text-font": ["Noto Sans Regular"], "text-size": 12,
                         "symbol-placement": placement},
              "paint": {"text-color": "#5D7896", "text-halo-color": C["halo"], "text-halo-width": 1}})
L.append({"id": "road-label", "type": "symbol", "source": SRC, "source-layer": "transportation_name", "minzoom": 13,
          "filter": ["!", cls("motorway", "rail", "transit", "ferry")],
          "layout": {"text-field": ["coalesce", ["get", "name"], ""], "text-font": ["Noto Sans Regular"],
                     "text-size": ["interpolate", ["linear"], ["zoom"], 13, 11, 17, 14], "symbol-placement": "line",
                     "text-max-angle": 30, "symbol-spacing": 320, "text-padding": 4},
          "paint": {"text-color": C["label"], "text-halo-color": C["halo"], "text-halo-width": 1.5}})
L.append({"id": "road-shield", "type": "symbol", "source": SRC, "source-layer": "transportation_name", "minzoom": 9,
          "filter": ["all", ["has", "ref"], ["<=", ["get", "ref_length"], 6], cls("motorway", "trunk", "primary")],
          "layout": {"text-field": ["get", "ref"], "text-font": ["Noto Sans Bold"], "text-size": 12,
                     "icon-image": "woozy-shield", "icon-text-fit": "both", "icon-text-fit-padding": [3, 6, 3, 6],
                     "symbol-placement": "line", "symbol-spacing": 500, "icon-rotation-alignment": "viewport",
                     "text-rotation-alignment": "viewport"},
          "paint": {"text-color": "#111214"}})
L.append({"id": "place-minor", "type": "symbol", "source": SRC, "source-layer": "place", "minzoom": 11,
          "filter": cls("suburb", "village", "hamlet", "neighbourhood", "quarter"),
          "layout": {"text-field": ["coalesce", ["get", "name"], ""], "text-font": ["Noto Sans Regular"],
                     "text-size": ["interpolate", ["linear"], ["zoom"], 11, 11, 15, 14], "text-transform": "uppercase",
                     "text-letter-spacing": 0.08, "text-max-width": 8},
          "paint": {"text-color": "#8C929A", "text-halo-color": C["halo"], "text-halo-width": 1.5}})
L.append({"id": "place-major", "type": "symbol", "source": SRC, "source-layer": "place", "maxzoom": 14,
          "filter": cls("city", "town"),
          "layout": {"text-field": ["coalesce", ["get", "name"], ""], "text-font": ["Noto Sans Bold"],
                     "text-size": ["interpolate", ["linear"], ["zoom"], 6, 11, 12, 18], "text-max-width": 8},
          "paint": {"text-color": "#E3E6EA", "text-halo-color": C["halo"], "text-halo-width": 1.5}})

style = {"version": 8, "name": "Woozy Dark",
         "sources": {SRC: {"type": "vector", "url": "{{TILES_URL}}",
                           "attribution": "© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors"}},
         "glyphs": "{{GLYPHS_URL}}", "layers": L}
json.dump(style, open(sys.argv[1], "w"), indent=1)
print(len(L), "layers")
