CREATE TABLE functional_building_block (
    code TEXT PRIMARY KEY,
    url TEXT
);

INSERT INTO functional_building_block (code, url) VALUES
    ('ACCESS_TO_METERING_AND_CONSUMPTION_DATA', 'https://architecture.insieme.energy/reference-models/regulated-domain/T4-2_01-access-to-metering-and-consumption-data/T4-2_01-access-to-metering-and-consumption-data.html'),
    ('ACCESS_TO_BASIC_MASTER_DATA', 'https://architecture.insieme.energy/reference-models/regulated-domain/T4-2_02-access-to-basic-master-data/T4-2_02-access-to-basic-master-data.html'),
    ('ACCESS_TO_IMPLICIT_FLEXIBILITY_SIGNALS', 'https://architecture.insieme.energy/reference-models/regulated-domain/T4-2_03-access-to-implicit-flexibility-signals/T4-2_03-access-to-implicit-flexibility-signals.html'),
    ('CUSTOMER_SWITCHING', 'https://architecture.insieme.energy/reference-models/regulated-domain/T4-2_04-customer-switching/T4-2_04-customer-switching.html'),
    ('COLLECTIVE_SELF_CONSUMPTION_AND_ENERGY_COMMUNITIES', 'https://architecture.insieme.energy/reference-models/regulated-domain/T4-3_01-collective-energy-sharing/T4-3_01-collective-energy-sharing.html'),
    ('FLEXIBLE_CONNECTION_AGREEMENTS', 'https://architecture.insieme.energy/reference-models/regulated-domain/T4-4_01-flexible-connection-agreements/T4-4_01-flexible-connection-agreements.html'),
    ('DEMAND_RESPONSE_PROCEDURES', 'https://architecture.insieme.energy/reference-models/regulated-domain/T4-4_02-ir-demand-response/T4-4_02-ir-demand-response.html'),
    ('FORECAST_AND_PLANNING_DATA', 'https://architecture.insieme.energy/reference-models/generic-data-sharing/generic-data-sharing.html');

ALTER TABLE reference_data_object
    ADD COLUMN building_block TEXT REFERENCES functional_building_block(code);
