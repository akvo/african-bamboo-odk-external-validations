# User Acceptance Criteria

## User Story
**As a** field enumerator
**I want to** download previously collected data before I go into the field
**So that** it is available to perform geoshape overlap validations while I collect data

## Acceptance Criteria

### Initial Download
**Given** I have downloaded and installed the external validation app
**When** I go into the app and enter username, password, server url and form id (uri)
**And** I click download data
**Then** I should see a loading spinner
**And** the data should download and save to a local database

### Download Completion
**When** the download is complete
**Then** I should see confirmation that the data download is complete
**And** I should see the number of form entries downloaded and the submission date of the latest form
**And** I should see a button to resync and refresh the data

### Data Resync
**When** I click the button I should see the same loading spinner as the initial download
**When** the sync is complete
**Then** I should see confirmation that the sync is complete which includes the number of added or updated records and the latest record submission time
