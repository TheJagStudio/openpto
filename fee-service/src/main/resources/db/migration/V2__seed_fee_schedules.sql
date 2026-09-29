-- Seed fee schedules. ALL AMOUNTS ARE ILLUSTRATIVE: they are modeled on the USPTO fee schedule
-- effective 2025-01-19 (FY2025) and the prior schedule (FY2023, 2022-12-29 -> 2025-01-18), but they
-- are not an authoritative statement of USPTO fees. Small ~= 40% and micro ~= 20% of the large-entity
-- amount; every amount is stored explicitly (no runtime percentage math).

insert into fee_schedule (code, name, effective_from, effective_to, notes) values
    ('FY2025', 'USPTO fee schedule effective January 19, 2025 (illustrative)', date '2025-01-19', null,
     'Illustrative amounts modeled on the USPTO fee setting rule effective 2025-01-19. Not legal or official fee advice.'),
    ('FY2023', 'USPTO fee schedule effective December 29, 2022 (illustrative)', date '2022-12-29', date '2025-01-18',
     'Illustrative amounts modeled on the USPTO fee schedule in force 2022-12-29 to 2025-01-18. Not legal or official fee advice.');

-- ---------------------------------------------------------------- FY2025 --
insert into fee_item (schedule_id, fee_code, description, category, fee_group,
                      large_entity, small_entity, micro_entity, unit, display_order)
select s.id, v.fee_code, v.description, v.category, v.fee_group, v.large_entity, v.small_entity, v.micro_entity, v.unit, v.display_order
from fee_schedule s
cross join (values
    ('UTIL_FILING',        'Basic filing fee - utility',                                   'PATENT', 'Filing',                  350,  140,  70,  'EACH',          10),
    ('UTIL_SEARCH',        'Utility search fee',                                           'PATENT', 'Search',                  770,  308,  154, 'EACH',          20),
    ('UTIL_EXAM',          'Utility examination fee',                                      'PATENT', 'Examination',             880,  352,  176, 'EACH',          30),
    ('DESIGN_FILING',      'Basic filing fee - design',                                    'PATENT', 'Filing',                  300,  120,  60,  'EACH',          40),
    ('DESIGN_SEARCH',      'Design search fee',                                            'PATENT', 'Search',                  160,  64,   32,  'EACH',          50),
    ('DESIGN_EXAM',        'Design examination fee',                                       'PATENT', 'Examination',             640,  256,  128, 'EACH',          60),
    ('PLANT_FILING',       'Basic filing fee - plant',                                     'PATENT', 'Filing',                  350,  140,  70,  'EACH',          70),
    ('PLANT_SEARCH',       'Plant search fee',                                             'PATENT', 'Search',                  480,  192,  96,  'EACH',          80),
    ('PLANT_EXAM',         'Plant examination fee',                                        'PATENT', 'Examination',             700,  280,  140, 'EACH',          90),
    ('REISSUE_FILING',     'Basic filing fee - reissue',                                   'PATENT', 'Filing',                  350,  140,  70,  'EACH',         100),
    ('REISSUE_SEARCH',     'Reissue search fee',                                           'PATENT', 'Search',                  770,  308,  154, 'EACH',         110),
    ('REISSUE_EXAM',       'Reissue examination fee',                                      'PATENT', 'Examination',             2860, 1144, 572, 'EACH',         120),
    ('PROV_FILING',        'Provisional application filing fee',                           'PATENT', 'Filing',                  325,  130,  65,  'EACH',         130),
    ('CLAIM_INDEP_OVER_3', 'Each independent claim in excess of 3',                        'PATENT', 'Excess claims',           600,  240,  120, 'PER_CLAIM',    140),
    ('CLAIM_OVER_20',      'Each claim in excess of 20',                                   'PATENT', 'Excess claims',           200,  80,   40,  'PER_CLAIM',    150),
    ('CLAIM_MULTI_DEP',    'Multiple dependent claim',                                     'PATENT', 'Excess claims',           925,  370,  185, 'EACH',         160),
    ('APP_SIZE',           'Application size fee - each additional 50 sheets over 100',    'PATENT', 'Application size',        440,  176,  88,  'PER_50_SHEETS',170),
    ('NON_ELECTRONIC',     'Non-electronic filing fee (utility only; micro pays small rate)', 'PATENT', 'Surcharges',           400,  200,  200, 'EACH',         180),
    ('LATE_SURCHARGE',     'Surcharge - late filing fee, search fee, examination fee or oath', 'PATENT', 'Surcharges',          160,  64,   32,  'EACH',         190),
    ('EXT_1M',             'Extension for response within first month',                    'PATENT', 'Extensions of time',      235,  94,   47,  'EACH',         200),
    ('EXT_2M',             'Extension for response within second month',                   'PATENT', 'Extensions of time',      530,  212,  106, 'EACH',         210),
    ('EXT_3M',             'Extension for response within third month',                    'PATENT', 'Extensions of time',      1260, 504,  252, 'EACH',         220),
    ('EXT_4M',             'Extension for response within fourth month',                   'PATENT', 'Extensions of time',      2010, 804,  402, 'EACH',         230),
    ('EXT_5M',             'Extension for response within fifth month',                    'PATENT', 'Extensions of time',      2760, 1104, 552, 'EACH',         240),
    ('RCE_FIRST',          'Request for continued examination (RCE) - first request',      'PATENT', 'Continued examination',   1500, 600,  300, 'EACH',         250),
    ('RCE_SUBSEQUENT',     'Request for continued examination (RCE) - second and subsequent', 'PATENT', 'Continued examination', 2860, 1144, 572, 'EACH',        260),
    ('TRACK_ONE',          'Prioritized examination (Track One)',                          'PATENT', 'Prioritized examination', 4515, 1806, 903, 'EACH',         270),
    ('MAINT_3_5',          'Maintenance fee due at 3.5 years',                             'PATENT', 'Maintenance',             2150, 860,  430, 'EACH',         280),
    ('MAINT_7_5',          'Maintenance fee due at 7.5 years',                             'PATENT', 'Maintenance',             4040, 1616, 808, 'EACH',         290),
    ('MAINT_11_5',         'Maintenance fee due at 11.5 years',                            'PATENT', 'Maintenance',             8280, 3312, 1656,'EACH',         300),
    ('MAINT_SURCHARGE',    'Surcharge - maintenance fee paid during 6-month grace period', 'PATENT', 'Maintenance',             540,  216,  108, 'EACH',         310),
    ('TM_BASE_APP',        'Base application (per class)',                                 'TRADEMARK', 'Application',          350,  350,  350, 'PER_CLASS',    400),
    ('TM_INSUFFICIENT_INFO','Surcharge - insufficient information (per class)',            'TRADEMARK', 'Application',          100,  100,  100, 'PER_CLASS',    410),
    ('TM_FREE_FORM',       'Surcharge - free-form text identification (per class)',        'TRADEMARK', 'Application',          200,  200,  200, 'PER_CLASS',    420),
    ('TM_EXTRA_CHARS',     'Surcharge - each additional 1,000 characters (per class)',     'TRADEMARK', 'Application',          200,  200,  200, 'PER_CLASS',    430),
    ('TM_SOU',             'Statement of use / allegation of use (per class)',             'TRADEMARK', 'Intent to use',        150,  150,  150, 'PER_CLASS',    440),
    ('TM_EXT_SOU',         'Request for extension of time to file a statement of use (per class)', 'TRADEMARK', 'Intent to use', 125, 125, 125, 'PER_CLASS',  450),
    ('TM_SEC8',            'Section 8 declaration of use (per class)',                     'TRADEMARK', 'Post-registration',    325,  325,  325, 'PER_CLASS',    460),
    ('TM_SEC15',           'Section 15 declaration of incontestability (per class)',       'TRADEMARK', 'Post-registration',    250,  250,  250, 'PER_CLASS',    470),
    ('TM_SEC9',            'Section 9 renewal application (per class)',                    'TRADEMARK', 'Post-registration',    325,  325,  325, 'PER_CLASS',    480),
    ('TM_SEC8_GRACE',      'Grace period surcharge - Section 8 (per class)',               'TRADEMARK', 'Post-registration',    100,  100,  100, 'PER_CLASS',    490),
    ('TM_SEC9_GRACE',      'Grace period surcharge - Section 9 (per class)',               'TRADEMARK', 'Post-registration',    100,  100,  100, 'PER_CLASS',    500)
) as v(fee_code, description, category, fee_group, large_entity, small_entity, micro_entity, unit, display_order)
where s.code = 'FY2025';

-- ---------------------------------------------------------------- FY2023 --
-- The FY2023 trademark schedule had a single TEAS application fee; the base-application surcharges
-- (insufficient information, free-form text, extra characters) did not exist yet.
insert into fee_item (schedule_id, fee_code, description, category, fee_group,
                      large_entity, small_entity, micro_entity, unit, display_order)
select s.id, v.fee_code, v.description, v.category, v.fee_group, v.large_entity, v.small_entity, v.micro_entity, v.unit, v.display_order
from fee_schedule s
cross join (values
    ('UTIL_FILING',        'Basic filing fee - utility',                                   'PATENT', 'Filing',                  320,  128,  64,  'EACH',          10),
    ('UTIL_SEARCH',        'Utility search fee',                                           'PATENT', 'Search',                  700,  280,  140, 'EACH',          20),
    ('UTIL_EXAM',          'Utility examination fee',                                      'PATENT', 'Examination',             800,  320,  160, 'EACH',          30),
    ('DESIGN_FILING',      'Basic filing fee - design',                                    'PATENT', 'Filing',                  280,  112,  56,  'EACH',          40),
    ('DESIGN_SEARCH',      'Design search fee',                                            'PATENT', 'Search',                  160,  64,   32,  'EACH',          50),
    ('DESIGN_EXAM',        'Design examination fee',                                       'PATENT', 'Examination',             640,  256,  128, 'EACH',          60),
    ('PLANT_FILING',       'Basic filing fee - plant',                                     'PATENT', 'Filing',                  320,  128,  64,  'EACH',          70),
    ('PLANT_SEARCH',       'Plant search fee',                                             'PATENT', 'Search',                  440,  176,  88,  'EACH',          80),
    ('PLANT_EXAM',         'Plant examination fee',                                        'PATENT', 'Examination',             660,  264,  132, 'EACH',          90),
    ('REISSUE_FILING',     'Basic filing fee - reissue',                                   'PATENT', 'Filing',                  320,  128,  64,  'EACH',         100),
    ('REISSUE_SEARCH',     'Reissue search fee',                                           'PATENT', 'Search',                  700,  280,  140, 'EACH',         110),
    ('REISSUE_EXAM',       'Reissue examination fee',                                      'PATENT', 'Examination',             2480, 992,  496, 'EACH',         120),
    ('PROV_FILING',        'Provisional application filing fee',                           'PATENT', 'Filing',                  300,  120,  60,  'EACH',         130),
    ('CLAIM_INDEP_OVER_3', 'Each independent claim in excess of 3',                        'PATENT', 'Excess claims',           480,  192,  96,  'PER_CLAIM',    140),
    ('CLAIM_OVER_20',      'Each claim in excess of 20',                                   'PATENT', 'Excess claims',           100,  40,   20,  'PER_CLAIM',    150),
    ('CLAIM_MULTI_DEP',    'Multiple dependent claim',                                     'PATENT', 'Excess claims',           860,  344,  172, 'EACH',         160),
    ('APP_SIZE',           'Application size fee - each additional 50 sheets over 100',    'PATENT', 'Application size',        420,  168,  84,  'PER_50_SHEETS',170),
    ('NON_ELECTRONIC',     'Non-electronic filing fee (utility only; micro pays small rate)', 'PATENT', 'Surcharges',           400,  200,  200, 'EACH',         180),
    ('LATE_SURCHARGE',     'Surcharge - late filing fee, search fee, examination fee or oath', 'PATENT', 'Surcharges',          160,  64,   32,  'EACH',         190),
    ('EXT_1M',             'Extension for response within first month',                    'PATENT', 'Extensions of time',      220,  88,   44,  'EACH',         200),
    ('EXT_2M',             'Extension for response within second month',                   'PATENT', 'Extensions of time',      640,  256,  128, 'EACH',         210),
    ('EXT_3M',             'Extension for response within third month',                    'PATENT', 'Extensions of time',      1480, 592,  296, 'EACH',         220),
    ('EXT_4M',             'Extension for response within fourth month',                   'PATENT', 'Extensions of time',      2320, 928,  464, 'EACH',         230),
    ('EXT_5M',             'Extension for response within fifth month',                    'PATENT', 'Extensions of time',      3160, 1264, 632, 'EACH',         240),
    ('RCE_FIRST',          'Request for continued examination (RCE) - first request',      'PATENT', 'Continued examination',   1360, 544,  272, 'EACH',         250),
    ('RCE_SUBSEQUENT',     'Request for continued examination (RCE) - second and subsequent', 'PATENT', 'Continued examination', 2000, 800, 400, 'EACH',         260),
    ('TRACK_ONE',          'Prioritized examination (Track One)',                          'PATENT', 'Prioritized examination', 4515, 1806, 903, 'EACH',         270),
    ('MAINT_3_5',          'Maintenance fee due at 3.5 years',                             'PATENT', 'Maintenance',             2000, 800,  400, 'EACH',         280),
    ('MAINT_7_5',          'Maintenance fee due at 7.5 years',                             'PATENT', 'Maintenance',             3760, 1504, 752, 'EACH',         290),
    ('MAINT_11_5',         'Maintenance fee due at 11.5 years',                            'PATENT', 'Maintenance',             7700, 3080, 1540,'EACH',         300),
    ('MAINT_SURCHARGE',    'Surcharge - maintenance fee paid during 6-month grace period', 'PATENT', 'Maintenance',             500,  200,  100, 'EACH',         310),
    ('TM_BASE_APP',        'TEAS application filing (per class)',                          'TRADEMARK', 'Application',          350,  350,  350, 'PER_CLASS',    400),
    ('TM_SOU',             'Statement of use / allegation of use (per class)',             'TRADEMARK', 'Intent to use',        100,  100,  100, 'PER_CLASS',    440),
    ('TM_EXT_SOU',         'Request for extension of time to file a statement of use (per class)', 'TRADEMARK', 'Intent to use', 125, 125, 125, 'PER_CLASS',  450),
    ('TM_SEC8',            'Section 8 declaration of use (per class)',                     'TRADEMARK', 'Post-registration',    225,  225,  225, 'PER_CLASS',    460),
    ('TM_SEC15',           'Section 15 declaration of incontestability (per class)',       'TRADEMARK', 'Post-registration',    200,  200,  200, 'PER_CLASS',    470),
    ('TM_SEC9',            'Section 9 renewal application (per class)',                    'TRADEMARK', 'Post-registration',    300,  300,  300, 'PER_CLASS',    480),
    ('TM_SEC8_GRACE',      'Grace period surcharge - Section 8 (per class)',               'TRADEMARK', 'Post-registration',    100,  100,  100, 'PER_CLASS',    490),
    ('TM_SEC9_GRACE',      'Grace period surcharge - Section 9 (per class)',               'TRADEMARK', 'Post-registration',    100,  100,  100, 'PER_CLASS',    500)
) as v(fee_code, description, category, fee_group, large_entity, small_entity, micro_entity, unit, display_order)
where s.code = 'FY2023';
