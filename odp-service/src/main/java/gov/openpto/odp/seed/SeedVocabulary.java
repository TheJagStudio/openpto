package gov.openpto.odp.seed;

import java.util.List;
import java.util.Map;

/** Static vocabulary for the mock data generator (all names and companies are fictional). */
final class SeedVocabulary {

    private SeedVocabulary() {
    }

    /** A technology area: CPC groups, art unit prefix, and phrase fragments for titles, abstracts and claims. */
    record Tech(
            String subclass,
            List<String> groups,
            String artUnitPrefix,
            List<String> domains,
            List<String> subjects,
            List<String> features,
            List<String> components,
            List<String> purposes) {
    }

    static final List<Tech> TECHS = List.of(
            new Tech("A61B", List.of("A61B5/0205", "A61B5/145", "A61B8/08", "A61B34/30"), "379",
                    List.of("Medical Devices", "Health Technologies", "Surgical Systems"),
                    List.of("wearable physiological monitor", "ultrasound imaging probe", "surgical robotic arm",
                            "continuous glucose sensor", "endoscopic camera assembly"),
                    List.of("a flexible printed electrode array", "adaptive motion-artifact filtering",
                            "a disposable sterile sheath", "haptic force feedback", "low-power wireless telemetry"),
                    List.of("a sensor housing", "a signal processing circuit", "a battery compartment",
                            "a wireless transceiver", "an adhesive patch", "a display unit"),
                    List.of("monitoring patient vital signs", "detecting cardiac arrhythmia",
                            "guiding minimally invasive surgery", "estimating blood glucose levels")),
            new Tech("A61K", List.of("A61K9/20", "A61K31/4439", "A61K39/395", "A61K47/69"), "161",
                    List.of("Therapeutics", "Pharmaceuticals", "Biopharma"),
                    List.of("sustained-release oral formulation", "lipid nanoparticle composition",
                            "monoclonal antibody formulation", "topical dermatological cream", "transdermal delivery patch"),
                    List.of("a pH-responsive polymer coating", "an ionizable cationic lipid",
                            "reduced aggregation at high concentration", "a penetration enhancer", "extended shelf stability"),
                    List.of("an active pharmaceutical ingredient", "a pharmaceutically acceptable carrier",
                            "a stabilizing excipient", "a buffering agent", "a surfactant"),
                    List.of("treating inflammatory skin disorders", "delivering mRNA therapeutics",
                            "managing chronic pain", "treating autoimmune disease")),
            new Tech("A47J", List.of("A47J31/44", "A47J37/06", "A47J43/07"), "376",
                    List.of("Home Appliances", "Kitchen Products", "Housewares"),
                    List.of("espresso brewing apparatus", "countertop air fryer", "smart blender", "coffee grinder",
                            "milk frothing device"),
                    List.of("a pressure-profiling pump", "a convection heating element", "load-sensing motor control",
                            "a ceramic burr set", "a steam wand with temperature sensing"),
                    List.of("a water reservoir", "a brewing chamber", "a heating element", "a control panel",
                            "a removable basket"),
                    List.of("preparing hot beverages", "cooking food with circulated hot air", "emulsifying ingredients")),
            new Tech("B25J", List.of("B25J9/16", "B25J15/08", "B25J13/08"), "366",
                    List.of("Robotics", "Automation", "Motion Systems"),
                    List.of("robotic gripper", "collaborative robot", "mobile manipulation platform", "end effector",
                            "warehouse picking robot"),
                    List.of("soft pneumatic fingers", "torque sensing at each joint", "vision-guided grasp planning",
                            "collision detection and safe stop", "a quick-change tool interface"),
                    List.of("a robot base", "an articulated arm", "a gripper assembly", "a camera module",
                            "a motion controller"),
                    List.of("picking items from bins", "assembling components", "handling fragile objects")),
            new Tech("B60L", List.of("B60L53/16", "B60L58/12", "B60L53/62"), "283",
                    List.of("Mobility", "EV Infrastructure", "Charging Solutions"),
                    List.of("electric vehicle charging station", "vehicle battery thermal management system",
                            "onboard charger", "wireless charging pad", "battery swapping station"),
                    List.of("bidirectional power flow", "a liquid-cooled charging cable", "dynamic load balancing",
                            "foreign object detection", "automated connector alignment"),
                    List.of("a power converter", "a charging connector", "a battery pack", "a coolant loop",
                            "a vehicle communication module"),
                    List.of("charging electric vehicles", "extending battery life", "balancing grid load")),
            new Tech("B33Y", List.of("B33Y10/00", "B33Y30/00", "B33Y70/00"), "174",
                    List.of("Additive Manufacturing", "3D Printing", "Fabrication"),
                    List.of("additive manufacturing system", "powder bed fusion apparatus", "extrusion print head",
                            "photopolymer resin", "build platform"),
                    List.of("multi-laser scanning", "closed-loop melt pool monitoring", "a heated build chamber",
                            "in-situ defect detection", "variable layer thickness"),
                    List.of("a recoater blade", "a build plate", "a laser source", "a nozzle", "a resin vat"),
                    List.of("fabricating metal parts", "printing multi-material objects")),
            new Tech("C07D", List.of("C07D401/14", "C07D471/04", "C07D487/04"), "162",
                    List.of("Pharmaceuticals", "Life Sciences", "Chemicals"),
                    List.of("heterocyclic kinase inhibitor", "substituted pyrimidine compound", "pyrazole derivative",
                            "crystalline salt form", "bicyclic heteroaryl compound"),
                    List.of("improved oral bioavailability", "selective JAK1 inhibition", "a novel polymorph",
                            "reduced hepatotoxicity", "enhanced aqueous solubility"),
                    List.of("a pyrimidine core", "a morpholine substituent", "a halogenated phenyl ring",
                            "a carboxamide linker"),
                    List.of("treating cancer", "inhibiting protein kinases", "treating fibrotic disease")),
            new Tech("C12N", List.of("C12N15/113", "C12N9/22", "C12N5/0783"), "163",
                    List.of("Biotherapeutics", "Genomics", "Cell Therapies"),
                    List.of("CRISPR gene editing system", "engineered T cell", "guide RNA construct",
                            "recombinant enzyme", "viral vector"),
                    List.of("reduced off-target activity", "a chimeric antigen receptor", "enhanced thermostability",
                            "tissue-specific expression", "a modified capsid protein"),
                    List.of("a Cas nuclease", "a guide sequence", "a promoter", "a signal peptide", "a transgene cassette"),
                    List.of("editing genomic DNA", "treating hematologic malignancies", "producing industrial enzymes")),
            new Tech("C22C", List.of("C22C21/02", "C22C38/04", "C22C19/05"), "173",
                    List.of("Materials", "Metals", "Alloys"),
                    List.of("aluminum alloy", "high-strength steel sheet", "nickel-based superalloy",
                            "corrosion-resistant coating", "magnesium alloy casting"),
                    List.of("a refined grain structure", "improved creep resistance", "a controlled silicon content",
                            "enhanced weldability", "low density"),
                    List.of("a matrix phase", "precipitate particles", "a surface oxide layer", "alloying elements"),
                    List.of("forming aircraft structural components", "forming automotive body panels",
                            "manufacturing turbine blades")),
            new Tech("D06F", List.of("D06F33/32", "D06F58/20", "D06F34/18"), "171",
                    List.of("Appliances", "Home Solutions", "Fabric Care"),
                    List.of("washing machine", "clothes dryer", "laundry treatment appliance",
                            "detergent dispensing system", "heat pump dryer"),
                    List.of("automatic load detection", "a heat pump dehumidifier", "adaptive drum speed control",
                            "a steam refresh cycle", "sensor-based detergent dosing"),
                    List.of("a rotatable drum", "a tub", "a drive motor", "a detergent reservoir", "a moisture sensor"),
                    List.of("washing garments", "drying laundry efficiently")),
            new Tech("D01F", List.of("D01F6/62", "D01F8/14", "D01F9/12"), "178",
                    List.of("Textiles", "Fibers", "Advanced Fabrics"),
                    List.of("bicomponent fiber", "recycled polyester filament", "carbon fiber precursor",
                            "nonwoven fabric", "moisture-wicking yarn"),
                    List.of("a sheath-core cross section", "bio-based polymer content", "antimicrobial additives",
                            "a high tensile modulus", "hollow channels"),
                    List.of("a polymer core", "a sheath layer", "a finishing agent", "staple fibers"),
                    List.of("making athletic apparel", "forming filtration media", "reinforcing composites")),
            new Tech("E21B", List.of("E21B43/26", "E21B47/12", "E21B10/42"), "367",
                    List.of("Energy Services", "Drilling Technologies", "Subsurface Systems"),
                    List.of("downhole drilling tool", "hydraulic fracturing system", "wellbore telemetry system",
                            "drill bit", "well completion assembly"),
                    List.of("polycrystalline diamond cutters", "mud-pulse telemetry", "a dissolvable frac plug",
                            "real-time torque measurement", "a rotary steerable mechanism"),
                    List.of("a drill string", "a bottom hole assembly", "a packer", "a sensor sub", "a pump"),
                    List.of("drilling subterranean formations", "stimulating hydrocarbon production",
                            "constructing geothermal wells")),
            new Tech("E05B", List.of("E05B47/00", "E05B49/00", "E05B65/10"), "367",
                    List.of("Security Products", "Smart Home", "Access Systems"),
                    List.of("electronic door lock", "smart deadbolt", "keyless entry system", "locking mechanism",
                            "access control reader"),
                    List.of("biometric authentication", "a motorized latch", "near-field communication",
                            "tamper detection", "battery-free energy harvesting"),
                    List.of("a lock housing", "a latch bolt", "a motor", "a keypad", "a wireless module"),
                    List.of("securing residential doors", "controlling building access")),
            new Tech("F03D", List.of("F03D7/02", "F03D1/06", "F03D80/50"), "374",
                    List.of("Wind Energy", "Renewables", "Power"),
                    List.of("wind turbine blade", "wind turbine controller", "pitch control system",
                            "nacelle cooling arrangement", "offshore wind turbine"),
                    List.of("a serrated trailing edge", "lidar-based yaw control", "load-reducing pitch adjustment",
                            "a segmented blade design", "ice detection"),
                    List.of("a rotor hub", "a nacelle", "a tower", "a generator", "a pitch actuator"),
                    List.of("generating electricity from wind", "reducing blade fatigue loads")),
            new Tech("F16H", List.of("F16H1/28", "F16H57/04", "F16H61/02"), "365",
                    List.of("Drivetrain", "Powertrain Systems", "Transmissions"),
                    List.of("planetary gear transmission", "continuously variable transmission",
                            "gearbox lubrication system", "electric drive unit", "dual-clutch transmission"),
                    List.of("a helical ring gear", "an integrated oil pump", "reduced gear whine",
                            "a compact coaxial layout", "torque vectoring"),
                    List.of("a sun gear", "a plurality of planet gears", "a carrier", "a housing", "an output shaft"),
                    List.of("transmitting torque in vehicles", "reducing drivetrain losses")),
            new Tech("G06F", List.of("G06F16/245", "G06F16/2453", "G06F9/50", "G06F21/62", "G06F3/0482"), "216",
                    List.of("Software", "Data Systems", "Computing"),
                    List.of("distributed database system", "query optimization engine", "container orchestration platform",
                            "data privacy framework", "data exploration interface"),
                    List.of("cost-based query planning", "automatic sharding", "workload-aware resource scheduling",
                            "differential privacy", "incremental materialized views"),
                    List.of("a query parser", "a storage engine", "a scheduler", "a metadata catalog", "a cache layer"),
                    List.of("processing large-scale analytical queries", "allocating computing resources",
                            "protecting personal data")),
            new Tech("G06N", List.of("G06N3/08", "G06N3/045", "G06N20/00", "G06N5/04"), "212",
                    List.of("AI", "Intelligence Labs", "Cognitive Systems"),
                    List.of("neural network training system", "machine learning model", "transformer-based language model",
                            "federated learning framework", "reinforcement learning agent"),
                    List.of("sparse attention", "gradient compression", "on-device inference", "knowledge distillation",
                            "calibrated uncertainty estimation"),
                    List.of("an embedding layer", "an attention module", "a training dataset", "a parameter server",
                            "an inference engine"),
                    List.of("classifying images", "generating natural language text", "predicting equipment failures")),
            new Tech("G06Q", List.of("G06Q20/40", "G06Q10/08", "G06Q30/02", "G06Q40/02"), "369",
                    List.of("Payments", "Commerce", "Financial Technologies"),
                    List.of("payment authorization system", "supply chain tracking platform", "dynamic pricing engine",
                            "fraud detection system", "digital wallet"),
                    List.of("tokenized card credentials", "blockchain-based provenance", "real-time risk scoring",
                            "geofenced offers", "multi-party settlement"),
                    List.of("a transaction server", "a merchant terminal", "a risk model", "a ledger",
                            "a customer account database"),
                    List.of("processing electronic payments", "tracking shipments", "detecting fraudulent transactions")),
            new Tech("G01S", List.of("G01S17/931", "G01S7/4817", "G01S13/931"), "364",
                    List.of("Sensing", "Autonomy", "Photonics"),
                    List.of("lidar sensor", "automotive radar system", "time-of-flight camera", "object detection system",
                            "sensor fusion module"),
                    List.of("solid-state beam steering", "frequency-modulated continuous wave ranging",
                            "interference mitigation", "a micro-electromechanical mirror", "point cloud compression"),
                    List.of("a laser emitter", "a photodetector array", "a scanning mirror", "a signal processor",
                            "a housing window"),
                    List.of("detecting obstacles around a vehicle", "mapping an environment")),
            new Tech("G16H", List.of("G16H50/20", "G16H10/60", "G16H40/67"), "368",
                    List.of("Health Informatics", "Digital Health", "Care Analytics"),
                    List.of("clinical decision support system", "electronic health record platform",
                            "remote patient monitoring service", "medication adherence system", "telehealth platform"),
                    List.of("risk stratification models", "interoperable data exchange", "automated alerting",
                            "natural language processing of clinical notes"),
                    List.of("a patient data repository", "an analytics server", "a clinician dashboard",
                            "a mobile application"),
                    List.of("predicting patient deterioration", "managing chronic conditions")),
            new Tech("G06T", List.of("G06T7/70", "G06T5/50", "G06T15/00", "G06T19/00"), "261",
                    List.of("Imaging", "Vision Systems", "Graphics"),
                    List.of("image processing pipeline", "3D reconstruction system", "augmented reality display system",
                            "depth estimation system", "image denoising system"),
                    List.of("multi-frame fusion", "neural radiance fields", "simultaneous localization and mapping",
                            "edge-aware filtering", "real-time rendering"),
                    List.of("an image sensor", "a graphics processor", "a pose estimator", "a frame buffer"),
                    List.of("enhancing low-light photographs", "overlaying virtual objects on a scene")),
            new Tech("H01M", List.of("H01M10/0562", "H01M10/0525", "H01M4/134", "H01M50/409", "H01M10/44"), "172",
                    List.of("Energy Systems", "Battery Technologies", "Power Cells"),
                    List.of("lithium-ion battery cell", "solid-state battery", "battery electrode assembly",
                            "battery management system", "battery module"),
                    List.of("a sulfide solid electrolyte", "a silicon-graphite composite anode",
                            "a ceramic-coated separator", "cell-level thermal runaway protection", "fast-charging capability"),
                    List.of("a cathode", "an anode", "an electrolyte", "a separator", "a current collector",
                            "a battery casing"),
                    List.of("storing electrical energy", "powering electric vehicles", "extending battery cycle life")),
            new Tech("H04L", List.of("H04L9/32", "H04L67/10", "H04L45/00", "H04L63/08"), "243",
                    List.of("Networks", "Secure Communications", "Cloud Networking"),
                    List.of("network authentication protocol", "content delivery network",
                            "software-defined networking controller", "secure messaging system", "zero-trust access gateway"),
                    List.of("post-quantum key exchange", "edge caching", "intent-based routing", "end-to-end encryption",
                            "mutual TLS"),
                    List.of("a client device", "an authentication server", "a routing table", "a certificate authority",
                            "a network interface"),
                    List.of("securing communications", "delivering media content", "routing network traffic")),
            new Tech("H04W", List.of("H04W72/04", "H04W24/02", "H04W76/10", "H04W4/80"), "246",
                    List.of("Wireless", "Telecom", "Mobile Networks"),
                    List.of("wireless communication system", "5G base station", "user equipment", "beam management system",
                            "network slicing controller"),
                    List.of("dynamic spectrum sharing", "adaptive beamforming", "low-latency uplink scheduling",
                            "reduced idle-mode power consumption", "multi-link operation"),
                    List.of("a transceiver", "an antenna array", "a baseband processor", "a scheduler"),
                    List.of("transmitting data over a cellular network", "allocating radio resources")),
            new Tech("H01L", List.of("H01L29/78", "H01L21/768", "H01L23/498", "H01L25/065"), "281",
                    List.of("Semiconductor", "Microelectronics", "Silicon"),
                    List.of("semiconductor device", "gate-all-around transistor", "chip package",
                            "through-silicon via structure", "memory device"),
                    List.of("a high-k metal gate", "backside power delivery", "hybrid bonding",
                            "a stacked nanosheet channel", "reduced parasitic capacitance"),
                    List.of("a substrate", "a gate electrode", "an interconnect layer", "a dielectric layer",
                            "a solder bump"),
                    List.of("increasing transistor density", "improving thermal dissipation")),
            new Tech("H02J", List.of("H02J3/38", "H02J7/00", "H02J3/32"), "283",
                    List.of("Grid Solutions", "Power Electronics", "Energy Storage"),
                    List.of("microgrid controller", "energy storage system", "solar inverter", "power distribution system",
                            "battery charging circuit"),
                    List.of("grid-forming control", "peak shaving", "maximum power point tracking", "islanding detection",
                            "state-of-charge balancing"),
                    List.of("an inverter", "a battery bank", "a controller", "a photovoltaic array", "a transformer"),
                    List.of("stabilizing an electrical grid", "storing renewable energy")),
            new Tech("H04N", List.of("H04N19/176", "H04N23/60", "H04N21/2343"), "248",
                    List.of("Media", "Video Technologies", "Imaging"),
                    List.of("video encoder", "camera module", "streaming media server", "image sensor",
                            "video conferencing system"),
                    List.of("adaptive bitrate encoding", "optical image stabilization", "HDR tone mapping",
                            "neural video compression"),
                    List.of("a lens assembly", "an image signal processor", "an encoder", "a frame buffer"),
                    List.of("compressing video data", "capturing high-dynamic-range images")));

    static final List<String> BENEFITS = List.of(
            "improves reliability under varying operating conditions",
            "reduces manufacturing cost",
            "increases efficiency relative to conventional approaches",
            "enhances user safety",
            "reduces power consumption",
            "enables a more compact integration",
            "shortens processing time",
            "extends service life");

    static final List<String> CLOSINGS = List.of(
            "Methods of manufacturing and operating the %s are also described.",
            "A controller may adjust operating parameters based on sensor feedback.",
            "The disclosed approach is compatible with existing production processes.",
            "Various embodiments allow the %s to be retrofitted into installed equipment.",
            "Experimental results demonstrate measurable improvement over prior designs.");

    static final List<String> DETAILS = List.of(
            "a thickness between 5 and 50 micrometers", "a polymer material", "at least two stacked layers",
            "a temperature sensor", "a wireless interface", "a removable cover", "a plurality of channels",
            "a coating that reduces friction", "a controller configured to adjust an operating parameter",
            "a ceramic material", "an aluminum alloy frame", "a memory storing calibration data",
            "a flexible substrate", "a locking tab", "a seal formed of an elastomer");

    static final List<String> STEPS = List.of(
            "determining a status based on sensor data", "transmitting a result to a remote server",
            "repeating the operating step at a predetermined interval", "calibrating the system prior to operation",
            "comparing a measured value with a threshold", "storing the result in a non-volatile memory",
            "displaying an indication to a user", "adjusting a parameter in response to the comparison");

    static final List<String> DESIGN_OBJECTS = List.of(
            "smartphone case", "wireless earbud", "electric scooter", "coffee maker", "desk lamp", "water bottle",
            "robot vacuum", "charging dock", "sneaker", "office chair", "smart speaker", "handheld game controller",
            "kitchen faucet", "bicycle helmet", "vehicle headlamp", "display screen with graphical user interface");

    static final List<String> PLANT_CROPS = List.of(
            "Apple tree", "Rose plant", "Blueberry plant", "Strawberry plant", "Hydrangea plant", "Grapevine",
            "Lavender plant", "Petunia plant", "Peach tree", "Hop plant");
    static final List<String> PLANT_CPC = List.of(
            "A01H6/7418", "A01H6/749", "A01H6/368", "A01H6/7409", "A01H6/78", "A01H6/88", "A01H6/50", "A01H6/82",
            "A01H6/7427", "A01H6/40");
    static final List<String> VARIETY_WORDS = List.of(
            "Crimson", "Dawn", "Velvet", "Starlight", "Honey", "Sierra", "Blush", "Aurora", "Ember", "Frost", "Coral",
            "Midnight", "Gold", "Sapphire", "Meadow", "Sunrise");

    // --- parties -------------------------------------------------------------------------------

    record Place(String city, String state, String country) {
    }

    static final Map<String, List<Place>> CITIES = Map.ofEntries(
            Map.entry("US", List.of(
                    new Place("Austin", "TX", "US"), new Place("San Jose", "CA", "US"), new Place("Seattle", "WA", "US"),
                    new Place("Boston", "MA", "US"), new Place("Raleigh", "NC", "US"), new Place("Pittsburgh", "PA", "US"),
                    new Place("Ann Arbor", "MI", "US"), new Place("Boulder", "CO", "US"), new Place("San Diego", "CA", "US"),
                    new Place("Minneapolis", "MN", "US"), new Place("Portland", "OR", "US"), new Place("Atlanta", "GA", "US"),
                    new Place("Chicago", "IL", "US"), new Place("Houston", "TX", "US"), new Place("Madison", "WI", "US"),
                    new Place("Palo Alto", "CA", "US"), new Place("Cambridge", "MA", "US"), new Place("Salt Lake City", "UT", "US"))),
            Map.entry("JP", List.of(new Place("Tokyo", null, "JP"), new Place("Osaka", null, "JP"),
                    new Place("Kyoto", null, "JP"), new Place("Nagoya", null, "JP"), new Place("Yokohama", null, "JP"))),
            Map.entry("DE", List.of(new Place("Munich", null, "DE"), new Place("Stuttgart", null, "DE"),
                    new Place("Berlin", null, "DE"), new Place("Hamburg", null, "DE"), new Place("Dresden", null, "DE"))),
            Map.entry("KR", List.of(new Place("Seoul", null, "KR"), new Place("Suwon", null, "KR"),
                    new Place("Daejeon", null, "KR"), new Place("Seongnam", null, "KR"))),
            Map.entry("CN", List.of(new Place("Shenzhen", null, "CN"), new Place("Shanghai", null, "CN"),
                    new Place("Beijing", null, "CN"), new Place("Hangzhou", null, "CN"))),
            Map.entry("FR", List.of(new Place("Paris", null, "FR"), new Place("Lyon", null, "FR"),
                    new Place("Grenoble", null, "FR"), new Place("Toulouse", null, "FR"))),
            Map.entry("GB", List.of(new Place("Cambridge", null, "GB"), new Place("London", null, "GB"),
                    new Place("Oxford", null, "GB"), new Place("Manchester", null, "GB"))),
            Map.entry("CH", List.of(new Place("Zurich", null, "CH"), new Place("Basel", null, "CH"),
                    new Place("Lausanne", null, "CH"))),
            Map.entry("SE", List.of(new Place("Stockholm", null, "SE"), new Place("Gothenburg", null, "SE"),
                    new Place("Lund", null, "SE"))),
            Map.entry("NL", List.of(new Place("Eindhoven", null, "NL"), new Place("Delft", null, "NL"),
                    new Place("Amsterdam", null, "NL"))),
            Map.entry("CA", List.of(new Place("Toronto", "ON", "CA"), new Place("Waterloo", "ON", "CA"),
                    new Place("Montreal", "QC", "CA"), new Place("Vancouver", "BC", "CA"))),
            Map.entry("IN", List.of(new Place("Bangalore", null, "IN"), new Place("Hyderabad", null, "IN"),
                    new Place("Pune", null, "IN"))),
            Map.entry("IL", List.of(new Place("Haifa", null, "IL"), new Place("Tel Aviv", null, "IL"))));

    /** Company-country weights (sum 100). */
    static final List<Map.Entry<String, Integer>> COMPANY_COUNTRIES = List.of(
            Map.entry("US", 48), Map.entry("JP", 12), Map.entry("DE", 8), Map.entry("KR", 8), Map.entry("CN", 8),
            Map.entry("FR", 3), Map.entry("GB", 3), Map.entry("CH", 2), Map.entry("SE", 2), Map.entry("NL", 3),
            Map.entry("CA", 3));

    /** Inventor-country weights when not co-located with the assignee. */
    static final List<Map.Entry<String, Integer>> INVENTOR_COUNTRIES = List.of(
            Map.entry("US", 40), Map.entry("IN", 10), Map.entry("CN", 10), Map.entry("JP", 8), Map.entry("DE", 8),
            Map.entry("KR", 7), Map.entry("GB", 5), Map.entry("CA", 5), Map.entry("IL", 4), Map.entry("FR", 3));

    static final Map<String, List<String>> COMPANY_PREFIXES = Map.ofEntries(
            Map.entry("US", List.of("Northwind", "Bluestone", "Cobalt Ridge", "Helix", "Aurora", "Summit", "Silverline",
                    "Quantum Harbor", "Redwood", "Ironbridge", "Nimbus", "Solstice", "Crescent", "Vertex", "Pioneer",
                    "Meridian", "Oakridge", "Lumen", "Tidewater", "Granite Peak", "Brightwater", "Keystone", "Everfield")),
            Map.entry("JP", List.of("Kaminari", "Hoshizora", "Tsubasa", "Mirai", "Asahikawa", "Shinkai")),
            Map.entry("DE", List.of("Rheinwerk", "Alpenstahl", "Nordlicht", "Weserland", "Schwarzwald")),
            Map.entry("KR", List.of("Hanbit", "Saebyeok", "Daehan", "Mirae Nexus", "Hanul")),
            Map.entry("CN", List.of("Huaxin", "Jinlong", "Tianyu", "Zhongke Lianchuang", "Xinghe")),
            Map.entry("FR", List.of("Lumiere", "Belvedere", "Arcachon")),
            Map.entry("GB", List.of("Thameswell", "Pennine", "Ashbourne")),
            Map.entry("CH", List.of("Matterhorn", "Lemania")),
            Map.entry("SE", List.of("Norrsken", "Vasa")),
            Map.entry("NL", List.of("Oranjeveld", "Zeeland", "Maasvlakte")),
            Map.entry("CA", List.of("Maplecrest", "Laurentian", "Fundy")));

    static final Map<String, List<String>> COMPANY_SUFFIXES = Map.ofEntries(
            Map.entry("US", List.of(", Inc.", " LLC", " Corporation", " Technologies, Inc.")),
            Map.entry("JP", List.of(" Co., Ltd.", " K.K.", " Corporation")),
            Map.entry("DE", List.of(" GmbH", " AG")),
            Map.entry("KR", List.of(" Co., Ltd.")),
            Map.entry("CN", List.of(" Co., Ltd.")),
            Map.entry("FR", List.of(" S.A.", " SAS")),
            Map.entry("GB", List.of(" Ltd", " plc")),
            Map.entry("CH", List.of(" AG", " SA")),
            Map.entry("SE", List.of(" AB")),
            Map.entry("NL", List.of(" B.V.")),
            Map.entry("CA", List.of(" Inc.", " Ltd.")));

    record Names(List<String> first, List<String> last) {
    }

    static final Names US_NAMES = new Names(
            List.of("James", "Maria", "Wei", "Priya", "David", "Sarah", "Michael", "Elena", "Carlos", "Aisha", "Robert",
                    "Jennifer", "Daniel", "Laura", "Kevin", "Emily", "Thomas", "Olivia", "Andrew", "Fatima", "Rahul",
                    "Hannah", "Jonathan", "Mei", "Samuel", "Grace", "Nathan", "Sofia", "Brian", "Rachel"),
            List.of("Smith", "Johnson", "Garcia", "Chen", "Patel", "Nguyen", "Kim", "Rodriguez", "Miller", "Davis",
                    "Martinez", "Anderson", "Thompson", "Lee", "Walker", "Hernandez", "Okafor", "Kowalski", "Brennan",
                    "Delgado", "Sullivan", "Shah", "Park", "Rossi", "Novak", "Fischer", "Ramirez", "Coleman", "Ibrahim", "Wong"));

    static final Map<String, Names> NAMES = Map.ofEntries(
            Map.entry("US", US_NAMES),
            Map.entry("CA", US_NAMES),
            Map.entry("JP", new Names(
                    List.of("Hiroshi", "Yuki", "Takashi", "Aiko", "Kenji", "Haruka", "Satoshi", "Naoko", "Daisuke", "Emi"),
                    List.of("Tanaka", "Suzuki", "Watanabe", "Yamamoto", "Nakamura", "Kobayashi", "Sato", "Ito", "Takahashi", "Matsumoto"))),
            Map.entry("DE", new Names(
                    List.of("Lukas", "Anna", "Johannes", "Katrin", "Matthias", "Julia", "Stefan", "Lena", "Florian", "Sabine"),
                    List.of("Mueller", "Schmidt", "Schneider", "Fischer", "Weber", "Wagner", "Becker", "Hoffmann", "Schulz", "Koch"))),
            Map.entry("CH", new Names(
                    List.of("Luca", "Nina", "Marco", "Laura", "Reto", "Sophie"),
                    List.of("Meier", "Keller", "Brunner", "Favre", "Rochat", "Gerber"))),
            Map.entry("KR", new Names(
                    List.of("Min-jun", "Seo-yeon", "Ji-hoon", "Hye-jin", "Dong-hyun", "Su-bin", "Jae-won", "Eun-ji"),
                    List.of("Kim", "Lee", "Park", "Choi", "Jung", "Kang", "Cho", "Yoon"))),
            Map.entry("CN", new Names(
                    List.of("Wei", "Fang", "Lei", "Jing", "Hao", "Xiaoming", "Yan", "Jun", "Li", "Ming"),
                    List.of("Wang", "Li", "Zhang", "Liu", "Chen", "Yang", "Huang", "Zhao", "Wu", "Zhou"))),
            Map.entry("FR", new Names(
                    List.of("Camille", "Julien", "Elodie", "Nicolas", "Manon", "Antoine"),
                    List.of("Martin", "Bernard", "Dubois", "Moreau", "Laurent", "Lefevre"))),
            Map.entry("GB", new Names(
                    List.of("Oliver", "Charlotte", "Harry", "Amelia", "George", "Isla"),
                    List.of("Taylor", "Brown", "Wilson", "Evans", "Hughes", "Clarke"))),
            Map.entry("SE", new Names(
                    List.of("Erik", "Astrid", "Lars", "Ingrid", "Johan", "Elsa"),
                    List.of("Lindqvist", "Johansson", "Andersson", "Nilsson", "Berg", "Holm"))),
            Map.entry("NL", new Names(
                    List.of("Daan", "Sanne", "Bram", "Lotte", "Joris", "Femke"),
                    List.of("de Vries", "Bakker", "Visser", "Smit", "Mulder", "de Boer"))),
            Map.entry("IN", new Names(
                    List.of("Arjun", "Ananya", "Vikram", "Kavya", "Rohan", "Sneha", "Aditya", "Meera"),
                    List.of("Sharma", "Iyer", "Reddy", "Nair", "Gupta", "Menon", "Rao", "Kulkarni"))),
            Map.entry("IL", new Names(
                    List.of("Noa", "Yosef", "Tamar", "Eitan", "Maya", "Ariel"),
                    List.of("Levi", "Cohen", "Mizrahi", "Friedman", "Peretz", "Azoulay"))));

    // --- trademarks ------------------------------------------------------------------------------

    record NiceClass(int number, String goods, List<String> words, List<Integer> related) {
    }

    static final List<NiceClass> NICE_CLASSES = List.of(
            new NiceClass(3, "Cosmetics; skin care preparations, namely, facial cleansers, serums and moisturizers; non-medicated lip balm",
                    List.of("BEAUTY", "SKIN", "GLOW", "BOTANICA"), List.of(44, 5)),
            new NiceClass(5, "Dietary supplements; vitamin preparations; medicated skin creams",
                    List.of("HEALTH", "VITA", "CARE"), List.of(3, 44)),
            new NiceClass(9, "Downloadable computer software for data analytics; downloadable mobile application software for scheduling appointments; wireless earbuds",
                    List.of("LABS", "CLOUD", "AI", "TECH", "DIGITAL"), List.of(42, 41, 38)),
            new NiceClass(12, "Electric bicycles; electric scooters; automobile parts, namely, brake pads",
                    List.of("MOTORS", "RIDE", "MOTION"), List.of(39, 9)),
            new NiceClass(14, "Jewelry; watches; bracelets",
                    List.of("JEWELERS", "TIME", "GEMS"), List.of(18, 25)),
            new NiceClass(18, "Backpacks; handbags; leather wallets; travel bags",
                    List.of("GOODS", "TRAVEL", "CARRY"), List.of(25, 14)),
            new NiceClass(21, "Drinking bottles sold empty; mugs; kitchen utensils",
                    List.of("HOME", "KITCHENWARE"), List.of(30, 43)),
            new NiceClass(25, "Clothing, namely, t-shirts, sweatshirts, hats and jackets; footwear",
                    List.of("APPAREL", "OUTFITTERS", "WEAR", "THREADS"), List.of(18, 14, 35)),
            new NiceClass(28, "Toys, namely, building blocks and plush toys; board games; fitness equipment",
                    List.of("PLAY", "GAMES", "TOYS"), List.of(9, 41)),
            new NiceClass(29, "Nut butters; dried fruit snacks; yogurt",
                    List.of("FARMS", "PANTRY"), List.of(30, 32)),
            new NiceClass(30, "Coffee; coffee beans; tea; chocolate; bakery goods",
                    List.of("ROASTERS", "COFFEE", "BAKERY", "BREW"), List.of(43, 29, 21)),
            new NiceClass(32, "Sparkling water; energy drinks; beer",
                    List.of("BREWING", "SODA", "TONIC"), List.of(33, 29)),
            new NiceClass(33, "Wine; distilled spirits, namely, whiskey and gin",
                    List.of("DISTILLERY", "VINEYARDS", "CELLARS"), List.of(32, 43)),
            new NiceClass(35, "Online retail store services featuring clothing and accessories; business consulting services; advertising services",
                    List.of("CONSULTING", "MARKET", "PARTNERS"), List.of(25, 36, 42)),
            new NiceClass(36, "Financial advisory services; insurance brokerage; real estate management",
                    List.of("CAPITAL", "FINANCIAL", "WEALTH"), List.of(35)),
            new NiceClass(38, "Telecommunication services, namely, providing internet access; streaming of audio and video content",
                    List.of("CONNECT", "STREAM"), List.of(9, 41)),
            new NiceClass(39, "Transportation logistics services; package delivery; travel arrangement",
                    List.of("LOGISTICS", "EXPRESS", "VOYAGES"), List.of(12, 35)),
            new NiceClass(41, "Educational services, namely, conducting online courses in the field of computer programming; entertainment services, namely, live music performances",
                    List.of("ACADEMY", "STUDIOS", "LEARNING"), List.of(9, 28)),
            new NiceClass(42, "Software as a service (SAAS) services featuring software for project management; design and development of computer software; cloud computing",
                    List.of("SYSTEMS", "SOFTWARE", "CLOUD", "DATA"), List.of(9, 35)),
            new NiceClass(43, "Restaurant services; cafe services; catering services",
                    List.of("KITCHEN", "BISTRO", "CAFE", "TAVERN"), List.of(30, 33)),
            new NiceClass(44, "Medical clinic services; day spa services; veterinary services",
                    List.of("WELLNESS", "CLINIC", "SPA"), List.of(3, 5)),
            new NiceClass(45, "Legal services; online social networking services; personal security consultancy",
                    List.of("LEGAL", "GUARDIAN"), List.of(35)));

    static final List<String> MARK_WORDS = List.of(
            "BLUE HERON", "IRONWOOD", "NORTHSTAR", "VELVET", "SUMMIT", "EMBER", "SILVER FOX", "RED CEDAR", "TIDEPOOL",
            "MOONLIT", "GOLDEN HOUR", "WILD SAGE", "COPPER KETTLE", "ATLAS", "ZEPHYR", "NOVA", "ORBIT", "LUMEN",
            "PINNACLE", "HARBOR", "WILLOW", "FALCON", "JUNIPER", "AURORA", "QUANTA", "KESTREL", "OAK & ASH", "MERIDIAN",
            "SOLACE", "RIVERSTONE", "BRIGHTLINE", "PIXELPINE", "CLOUDBERRY", "HIGHLAND", "SEAGLASS", "TRAILHEAD",
            "BRAVO", "KINDRED", "MOSAIC", "NIMBUS", "LANTERN", "SPARROW", "GRANITE", "HALCYON", "BLACKBIRD");
    static final List<String> COINED_HEADS = List.of("ZY", "LO", "VA", "TRI", "NEX", "QUA", "MIRA", "SOL", "VEL", "KAI", "OMNI", "ARI");
    static final List<String> COINED_TAILS = List.of("VIA", "TEK", "ORA", "LUX", "IFY", "ENTA", "ORO", "LYX", "UMA", "ENDO");
    static final List<String> SOUND_MARKS = List.of(
            "THREE ASCENDING CHIMES", "A SHORT WHISTLED MELODY", "TWO-TONE DOORBELL SOUND",
            "A PERCUSSIVE DRUM ROLL FOLLOWED BY A CYMBAL", "A PURRING SOUND");
    static final List<String> STREETS = List.of(
            "Harbor Way", "Main Street", "Innovation Drive", "Market Street", "Oak Avenue", "Commerce Parkway",
            "River Road", "Elm Street", "Technology Boulevard", "Pine Street", "Lakeview Drive", "Broadway");
}
