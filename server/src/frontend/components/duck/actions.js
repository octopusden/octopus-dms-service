import types from './types'

const requestBuildInfo = () => ({
    type: types.REQUEST_BUILD_INFO
})

const receiveBuildInfo = (buildInfo) => ({
    type: types.RECEIVE_BUILD_INFO,
    buildInfo: buildInfo
})

const requestLoggedUser = () => ({
    type: types.REQUEST_LOGGED_USER
})

const receiveLoggedUser = (loggedUser) => ({
    type: types.RECEIVE_LOGGED_USER,
    loggedUser: loggedUser
})

const requestComponents = () => ({
    type: types.REQUEST_COMPONENTS
})

const requestComponentVersions = (componentId, minorVersion) => ({
    type: types.REQUEST_COMPONENT_VERSIONS,
    componentId: componentId,
    minorVersion: minorVersion
})

const receiveComponents = (components) => ({
    type: types.RECEIVE_COMPONENTS,
    components: components
})

const receiveComponentVersionsError = (componentId, minorVersion, errorMessage) => ({
    type: types.RECEIVE_COMPONENT_VERSIONS_ERROR,
    componentId: componentId,
    minorVersion: minorVersion,
    errorMessage: errorMessage,
    loadingError: true
})

const receiveComponentVersions = (componentId, minorVersion, versions) => ({
    type: types.RECEIVE_COMPONENT_VERSIONS,
    componentId: componentId,
    minorVersion: minorVersion,
    versions: versions
})

const expandComponent = (componentId) => ({
    type: types.EXPAND_COMPONENT,
    componentId: componentId
})

const closeComponent = (componentId) => ({
    type: types.CLOSE_COMPONENT,
    componentId: componentId
})

const expandMinorVersion = (componentId, minorVersion) => ({
    type: types.EXPAND_MINOR_VERSION,
    componentId: componentId,
    minorVersion: minorVersion
})

const closeMinorVersion = (componentId, minorVersion) => ({
    type: types.CLOSE_MINOR_VERSION,
    componentId: componentId,
    minorVersion: minorVersion
})

const selectVersion = (componentId, minorVersion, version) => ({
    type: types.SELECT_VERSION,
    componentId: componentId,
    minorVersion: minorVersion,
    version: version
})

const requestArtifactsList = (componentId, minorVersion, version) => ({
    type: types.REQUEST_ARTIFACTS_LIST,
    componentId: componentId,
    minorVersion: minorVersion,
    version: version
})

const receiveArtifactsList = (componentId, minorVersion, version, artifactsList) => ({
    type: types.RECEIVE_ARTIFACTS_LIST,
    componentId: componentId,
    minorVersion: minorVersion,
    version: version,
    artifactsList: artifactsList
})

const requestDocumentArtifact = (id) => ({
    type: types.REQUEST_DOCUMENT_ARTIFACT,
    id: id
})

const receiveDocumentArtifact = (id, displayName, isPrintable, documentText) => ({
    type: types.RECEIVE_DOCUMENT_ARTIFACT,
    id: id,
    displayName: displayName,
    isPrintable: isPrintable,
    documentText: documentText
})

const requestComponentMinorVersions = (componentId) => ({
    type: types.REQUEST_COMPONENT_MINOR_VERSIONS,
    componentId: componentId
})

const receiveComponentMinorVersions = (componentId, versions) => ({
    type: types.RECEIVE_COMPONENT_MINOR_VERSIONS,
    componentId: componentId,
    versions: versions
})

const receiveComponentMinorVersionsError = (componentId, errorMessage) => ({
    type: types.RECEIVE_COMPONENT_MINOR_VERSIONS_ERROR,
    componentId: componentId,
    errorMessage: errorMessage
})

const showError = (errorMessage) => ({
    type: types.ERROR_OCCURED,
    errorMessage: errorMessage
})

const hideError = () => ({
    type: types.HIDE_ERROR,
})

const toggleRc = () => ({
    type: types.TOGGLE_RC
})

const toggleAdminMode = () => ({
    type: types.TOGGLE_ADMIN_MODE
})

const handleComponentGroupTabChange = (selectedComponentGroupTab) => ({
    type: types.HANDLE_COMPONENT_GROUP_TAB_CHANGE,
    selectedComponentGroupTab: selectedComponentGroupTab
})

const deleteArtifact = (id) => ({
    type: types.DELETE_ARTIFACT,
    id: id
})

const successDeleteArtifact = (id) => ({
    type: types.SUCCESS_DELETE_ARTIFACT,
    id: id
})

const showConfirmation = (message, onConfirm) => ({
    type: types.SHOW_CONFIRMATION,
    message: message,
    onConfirm: onConfirm
})

const hideConfirmation = () => ({
    type: types.HIDE_CONFIRMATION
})

const requestSearch = (query) => ({
    type: types.REQUEST_SEARCH,
    query: query
})

const receiveSearch = (searchResult) => ({
    type: types.RECEIVE_SEARCH,
    searchResult: searchResult
})

const changeSearchQueryValid = (isQueryValid) => ({
    type: types.CHANGE_SEARCH_QUERY_VALID,
    searchQueryValid: isQueryValid
})

export default {
    requestBuildInfo,
    receiveBuildInfo,
    requestLoggedUser,
    receiveLoggedUser,
    requestComponents,
    requestComponentVersions,
    receiveComponents,
    receiveComponentVersionsError,
    receiveComponentVersions,
    expandComponent,
    closeComponent,
    requestArtifactsList,
    receiveArtifactsList,
    requestDocumentArtifact,
    receiveDocumentArtifact,
    deleteArtifact,
    successDeleteArtifact,
    showConfirmation,
    hideConfirmation,
    showError,
    hideError,
    toggleRc,
    toggleAdminMode,
    handleComponentGroupTabChange,
    requestSearch,
    receiveSearch,
    changeSearchQueryValid,
    requestComponentMinorVersions,
    receiveComponentMinorVersions,
    receiveComponentMinorVersionsError,
    expandMinorVersion,
    closeMinorVersion,
    selectVersion
}
