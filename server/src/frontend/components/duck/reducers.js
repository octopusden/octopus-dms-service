import types from './types'

const INITIAL_STATE = {
    buildInfo: {},
    loggedUser: {},

    components: {},

    loadingComponents: false,
    loadingArtifactsList: false,

    currentArtifacts: {
        selectedComponent: null,
        selectedMinor: null,
        selectedVersion: null,
        selectedDocument: {},
        artifactsList: []
    },

    confirmation: null,

    errorMessage: null,
    showRc: false,
    adminMode: false,

    searchQuery: '',
    searching: false,
    searchQueryValid: false,
    searchResult: []
}

const componentsReducer = (state = INITIAL_STATE, action) => {
    switch (action.type) {

        case types.RECEIVE_LOGGED_USER: {
            const {loggedUser} = action
            return {
                ...state,
                loggedUser: loggedUser
            }
        }

        case types.RECEIVE_BUILD_INFO: {
            const {buildInfo} = action
            return {
                ...state,
                buildInfo: buildInfo
            }
        }

        case types.REQUEST_COMPONENTS: {
            return {
                ...state,
                loadingComponents: true
            }
        }

        case types.RECEIVE_COMPONENTS: {
            const {components} = action
            return {
                ...state,
                components: components,
                loadingComponents: false
            }
        }

        case types.REQUEST_COMPONENT_VERSIONS: {
            const {componentId, minorVersion} = action
            const {components} = state
            return {
                ...state,
                components: {
                    ...components,
                    [componentId]: {
                        ...components[componentId],
                        minorVersions: {
                            ...components[componentId].minorVersions,
                            [minorVersion]: {
                                ...components[componentId].minorVersions[minorVersion],
                                loadingVersions: true,
                                versions: []
                            }
                        }
                    }
                }
            }
        }

        case types.RECEIVE_COMPONENT_VERSIONS: {
            const {componentId, minorVersion, versions} = action
            const {components} = state
            return {
                ...state,
                components: {
                    ...components,
                    [componentId]: {
                        ...components[componentId],
                        minorVersions: {
                            ...components[componentId].minorVersions,
                            [minorVersion]: {
                                ...components[componentId].minorVersions[minorVersion],
                                loadingVersions: false,
                                versions: versions
                            }
                        }
                    }
                }
            }
        }

        case types.RECEIVE_COMPONENT_VERSIONS_ERROR: {
            const {componentId, errorMessage, minorVersion} = action
            const {components} = state
            return {
                ...state,
                components: {
                    ...components,
                    [componentId]: {
                        ...components[componentId],
                        minorVersions: {
                            ...components[componentId].minorVersions,
                            [minorVersion]: {
                                loadingVersions: false,
                                loadingError: true,
                                loadingErrorMessage: errorMessage
                            }
                        }
                    }
                }
            }
        }

        case types.REQUEST_COMPONENT_MINOR_VERSIONS: {
            const {componentId} = action
            const {components} = state
            return {
                ...state,
                components: {
                    ...components,
                    [componentId]: {
                        ...components[componentId],
                        loadingMinorVersions: true,
                        loadingError: false,
                        minorVersions: {}
                    }
                }
            }
        }

        case types.RECEIVE_COMPONENT_MINOR_VERSIONS: {
            const {componentId, versions} = action
            const {components} = state
            return {
                ...state,
                components: {
                    ...components,
                    [componentId]: {
                        ...components[componentId],
                        loadingMinorVersions: false,
                        minorVersions: versions
                    }
                }
            }
        }

        case types.REQUEST_SEARCH: {
            const {query} = action
            return {
                ...state,
                searchQuery: query,
                searching: true
            }
        }

        case types.RECEIVE_SEARCH: {
            const {searchResult} = action
            return {
                ...state,
                searchResult: searchResult,
                searching: false
            }
        }

        case types.CHANGE_SEARCH_QUERY_VALID: {
            const {searchQueryValid} = action
            return {
                ...state,
                searchQueryValid: searchQueryValid
            }
        }

        case types.EXPAND_COMPONENT: {
            const {componentId} = action
            const {components} = state
            return {
                ...state,
                components: {
                    ...components,
                    [componentId]: {
                        ...components[componentId],
                        expand: true
                    }
                }
            }
        }

        case types.CLOSE_COMPONENT: {
            const {componentId} = action
            const {components} = state
            return {
                ...state,
                components: {
                    ...components,
                    [componentId]: {
                        ...components[componentId],
                        expand: false
                    }
                }
            }
        }

        case types.EXPAND_MINOR_VERSION: {
            const {componentId, minorVersion} = action
            const {components} = state
            return {
                ...state,
                components: {
                    ...components,
                    [componentId]: {
                        ...components[componentId],
                        minorVersions: {
                            ...components[componentId].minorVersions,
                            [minorVersion]: {
                                ...components[componentId].minorVersions[minorVersion],
                                expand: true
                            }
                        }
                    }
                }
            }
        }

        case types.CLOSE_MINOR_VERSION: {
            const {componentId, minorVersion} = action
            const {components} = state
            return {
                ...state,
                components: {
                    ...components,
                    [componentId]: {
                        ...components[componentId],
                        minorVersions: {
                            ...components[componentId].minorVersions,
                            [minorVersion]: {
                                ...components[componentId].minorVersions[minorVersion],
                                expand: false
                            }
                        }
                    }
                }
            }
        }

        case types.SELECT_VERSION: {
            const {componentId, minorVersion, version} = action
            return {
                ...state,
                loadingArtifactsList: false,
                currentArtifacts: {
                    loadingDocumentArtifact: false,
                    selectedComponent: componentId,
                    selectedMinor: minorVersion,
                    selectedVersion: version,
                    selectedDocument: {},
                    artifactsList: []
                }
            }
        }

        case types.REQUEST_ARTIFACTS_LIST: {
            const {componentId, minorVersion, version} = action
            return {
                ...state,
                loadingArtifactsList: true,
                currentArtifacts: {
                    loadingDocumentArtifact: false,
                    selectedComponent: componentId,
                    selectedMinor: minorVersion,
                    selectedVersion: version,
                    selectedDocument: {},
                    artifactsList: []
                }
            }
        }

        case types.RECEIVE_ARTIFACTS_LIST: {
            const {componentId, minorVersion, version, artifactsList} = action
            return {
                ...state,
                loadingArtifactsList: false,
                currentArtifacts: {
                    ...state.currentArtifacts,
                    selectedComponent: componentId,
                    selectedMinor: minorVersion,
                    selectedVersion: version,
                    artifactsList: artifactsList,
                    preview: {}
                }
            }
        }

        case types.REQUEST_DOCUMENT_ARTIFACT: {
            const {id} = action
            console.debug('REQUEST_DOCUMENT_ARTIFACT', id)
            return {
                ...state,
                currentArtifacts: {
                    ...state.currentArtifacts,
                    loadingDocumentArtifact: true,
                    selectedDocument: {
                        id: id
                    }
                }
            }
        }

        case types.RECEIVE_DOCUMENT_ARTIFACT: {
            const {id, displayName, isPrintable, documentText} = action
            return {
                ...state,
                currentArtifacts: {
                    ...state.currentArtifacts,
                    loadingDocumentArtifact: false,
                    selectedDocument: {
                        id: id,
                        displayName: displayName,
                        isPrintable: isPrintable,
                        documentText: documentText
                    }
                }
            }
        }

        case types.ERROR_OCCURED: {
            const {errorMessage} = action
            return {
                ...state,
                errorMessage: errorMessage
            }
        }

        case types.HIDE_ERROR: {
            return {
                ...state,
                errorMessage: null
            }
        }

        case types.TOGGLE_RC: {
            return {
                ...state,
                showRc: !state.showRc
            }
        }

        case types.TOGGLE_ADMIN_MODE: {
            return {
                ...state,
                adminMode: !state.adminMode
            }
        }

        case types.HANDLE_COMPONENT_GROUP_TAB_CHANGE: {
            const {selectedComponentGroupTab} = action
            return {
                ...state,
                selectedComponentGroupTab: selectedComponentGroupTab
            }
        }

        case types.SHOW_CONFIRMATION: {
            const {message, onConfirm} = action
            return {
                ...state,
                confirmation: {
                    message: message,
                    onConfirm: onConfirm
                }
            }
        }

        case types.HIDE_CONFIRMATION: {
            return {
                ...state,
                confirmation: null
            }
        }

        default:
            return state
    }
}

export default componentsReducer
