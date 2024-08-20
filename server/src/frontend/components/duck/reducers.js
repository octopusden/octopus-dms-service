import types from './types'
import get from "lodash/get"

const INITIAL_STATE = {
    buildInfo: {},
    loggedUser: {},

    components: {},

    loadingComponents: false,
    loadingArtifacts: false,

    currentArtifacts: {
        selectedComponent: null,
        selectedMinor: null,
        selectedVersion: null,
        selectedDocument: {},
        artifacts: [],
        meta: {
            ready: false
        }
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

        case types.RECEIVE_BUILD_INFO: {
            const {buildInfo} = action
            return {
                ...state,
                buildInfo: buildInfo
            }
        }

        case types.RECEIVE_LOGGED_USER: {
            const {loggedUser} = action
            return {
                ...state,
                loggedUser: loggedUser
            }
        }

        case types.REQUEST_COMPONENTS: {
            return {
                ...state,
                components: {},
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

        case types.REQUEST_VERSIONS: {
            const {componentId, minorVersion} = action
            return updateComponentMinorVersion(state, componentId, minorVersion, {loading: true, versions: {}})
        }

        case types.RECEIVE_VERSIONS: {
            const {componentId, minorVersion, versions} = action
            return updateComponentMinorVersion(state, componentId, minorVersion, {loading: false, versions: versions})
        }

        case types.EXPAND_VERSION: {
            const {componentId, minorVersion, version} = action
            return updateComponentVersion(state, componentId, minorVersion, version, {expand: true});
        }

        case types.CLOSE_VERSION: {
            const {componentId, minorVersion, version} = action
            return updateComponentVersion(state, componentId, minorVersion, version, {expand: false});
        }

        case types.RECEIVE_VERSIONS_ERROR: {
            const {componentId, errorMessage, minorVersion} = action
            return updateComponentMinorVersion(state, componentId, minorVersion, {
                loading: false,
                loadError: true,
                errorMessage: errorMessage,
                versions: {}
            })
        }

        case types.REQUEST_DEPENDENCIES: {
            const {componentId, minorVersion, version} = action
            return updateComponentVersion(state, componentId, minorVersion, version, {
                loading: true,
                loadError: false,
                dependencies: {}
            })
        }

        case types.RECEIVE_DEPENDENCIES: {
            const {componentId, minorVersion, version, dependencies} = action
            return updateComponentVersion(state, componentId, minorVersion, version, {
                loading: false,
                dependencies: dependencies
            })
        }

        case types.REQUEST_COMPONENT_MINOR_VERSIONS: {
            const {componentId} = action
            return updateComponent(state, componentId, {loading: true, loadError: false, minorVersions: {}})
        }

        case types.RECEIVE_COMPONENT_MINOR_VERSIONS: {
            const {componentId, versions} = action
            return updateComponent(state, componentId, {loading: false, minorVersions: versions})
        }

        case types.EXPAND_COMPONENT_MINOR_VERSION: {
            const {componentId, minorVersion} = action
            return updateComponentMinorVersion(state, componentId, minorVersion, {expand: true})
        }

        case types.CLOSE_COMPONENT_MINOR_VERSION: {
            const {componentId, minorVersion} = action
            return updateComponentMinorVersion(state, componentId, minorVersion, {expand: false})
        }

        case types.EXPAND_GROUPED_COMPONENT: {
            const {groupId, componentId} = action
            return updateGroupedComponent(state, groupId, componentId, {expand: true})
        }

        case types.CLOSE_GROUPED_COMPONENT: {
            const {groupId, componentId} = action
            return updateGroupedComponent(state, groupId, componentId, {expand: false})
        }

        case types.REQUEST_GROUPED_COMPONENT_MINOR_VERSIONS: {
            const {groupId, componentId} = action
            const data = {loading: true, loadError: false, errorMessage: null, minorVersions: {}};
            return updateGroupedComponent(state, groupId, componentId, data)
        }

        case types.RECEIVE_GROUPED_COMPONENT_MINOR_VERSIONS: {
            const {groupId, componentId, versions} = action
            const data = {loading: false, loadError: false, errorMessage: null, minorVersions: versions};
            return updateGroupedComponent(state, groupId, componentId, data)
        }

        case types.RECEIVE_GROUPED_COMPONENT_MINOR_VERSIONS_ERROR: {
            const {groupId, componentId, errorMessage} = action
            const data = {loading: false, loadError: true, errorMessage: errorMessage, minorVersions: {}};
            return updateGroupedComponent(state, groupId, componentId, data)
        }

        case types.EXPAND_GROUPED_COMPONENT_MINOR_VERSION: {
            const {groupId, componentId, minorVersion} = action
            return updateGroupedComponentMinorVersion(state, groupId, componentId, minorVersion, {expand: true})
        }

        case types.CLOSE_GROUPED_COMPONENT_MINOR_VERSION: {
            const {groupId, componentId, minorVersion} = action
            return updateGroupedComponentMinorVersion(state, groupId, componentId, minorVersion, {expand: false})
        }

        case types.REQUEST_GROUPED_COMPONENT_VERSIONS: {
            const {groupId, componentId, minorVersion} = action
            const data = {loading: true, loadError: false, versions: {}};
            return updateGroupedComponentMinorVersion(state, groupId, componentId, minorVersion, data)
        }

        case types.RECEIVE_GROUPED_COMPONENT_VERSIONS: {
            const {groupId, componentId, minorVersion, versions} = action
            const data = {loading: false, loadError: false, versions: versions};
            return updateGroupedComponentMinorVersion(state, groupId, componentId, minorVersion, data)
        }

        case types.SELECT_VERSION: {
            const {componentId, minorVersion, version} = action
            const {currentArtifacts, components} = state
            const selectedComponent = get(components, [componentId])
            return {
                ...state,
                loadingArtifacts: false,
                currentArtifacts: {
                    ...currentArtifacts,
                    loadingDocumentArtifact: false,
                    selectedComponent: componentId,
                    selectedMinor: minorVersion,
                    selectedVersion: version,
                    selectedDocument: {},
                    artifacts: [],
                    meta: {
                        componentId: componentId,
                        componentName: selectedComponent.name,
                        solution: selectedComponent.solution,
                        clientCode: selectedComponent.clientCode,
                        parentComponent: selectedComponent.parentComponent
                    }
                }
            }
        }

        case types.SELECT_DEPENDENCY: {
            const {solutionId, solutionMinor, solutionVersion, componentId, version} = action
            const {currentArtifacts, components} = state
            const dependencyId = `${componentId}:${version}`

            const selectedComponent = solutionId === componentId && solutionVersion === version
                ? get(components, [componentId])
                : get(components, [solutionId, 'minorVersions', solutionMinor, 'versions', solutionVersion, 'dependencies', dependencyId, 'component'])

            return {
                ...state,
                loadingArtifacts: false,
                currentArtifacts: {
                    ...currentArtifacts,
                    loadingDocumentArtifact: false,
                    selectedSolutionId: solutionId,
                    selectedSolutionMinor: solutionMinor,
                    selectedSolutionVersion: solutionVersion,
                    selectedComponent: componentId,
                    selectedMinor: null,
                    selectedVersion: version,
                    selectedDocument: {},
                    artifacts: [],
                    meta: {
                        componentId: componentId,
                        componentName: selectedComponent.name,
                        solution: selectedComponent.solution,
                        clientCode: selectedComponent.clientCode,
                        parentComponent: selectedComponent.parentComponent
                    }
                }
            }
        }

        case types.SELECT_GROUPED_COMPONENT_VERSION: {
            const {groupId, componentId, minorVersion, version} = action
            const {currentArtifacts, components} = state
            const selectedComponent = get(components, [groupId, 'subComponents', componentId])
            return {
                ...state,
                loadingArtifacts: false,
                currentArtifacts: {
                    ...currentArtifacts,
                    loadingDocumentArtifact: false,
                    selectedGroup: groupId,
                    selectedComponent: componentId,
                    selectedMinor: minorVersion,
                    selectedVersion: version,
                    selectedDocument: {},
                    artifacts: [],
                    meta: {
                        componentId: componentId,
                        componentName: selectedComponent.name,
                        solution: selectedComponent.solution,
                        clientCode: selectedComponent.clientCode,
                        parentComponent: selectedComponent.parentComponent
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
            return updateComponent(state, componentId, {expand: true})
        }

        case types.CLOSE_COMPONENT: {
            const {componentId} = action
            return updateComponent(state, componentId, {expand: false})
        }

        case types.REQUEST_ARTIFACTS: {
            return {
                ...state,
                loadingArtifacts: true
            }
        }

        case types.RECEIVE_ARTIFACTS: {
            const {artifacts} = action
            const build = artifacts.build
            return {
                ...state,
                loadingArtifacts: false,
                currentArtifacts: {
                    ...state.currentArtifacts,
                    artifacts: artifacts.artifacts,
                    meta: {
                        ...state.currentArtifacts.meta,
                        ready: true,
                        componentId: build.component,
                        version: build.version,
                        status: build.status,
                        promoted: build.promotedAt
                    },
                    preview: {}
                }
            }
        }

        case types.REQUEST_DOCUMENT_ARTIFACT: {
            const {id} = action
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
                currentArtifacts: {
                    selectedComponentGroupTab: selectedComponentGroupTab,
                    meta: {
                        ready: false
                    }
                }
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

function updateGroupedComponentMinorVersion(state, groupId, componentId, minorVersion, data) {
    const {components} = state
    const parentComponent = components[groupId];
    const subComponents = parentComponent.subComponents;
    const customComponent = subComponents[componentId];
    const componentMinors = customComponent.minorVersions;
    const minorVersions = {
        minorVersions: {
            ...componentMinors,
            [minorVersion]: {
                ...(componentMinors[minorVersion]),
                ...data
            }
        }
    }
    return updateGroupedComponent(state, groupId, componentId, minorVersions)
}

function updateGroupedComponent(state, groupId, componentId, data) {
    const {components} = state
    const parentComponent = components[groupId]
    const subComponents = parentComponent.subComponents
    const customComponent = subComponents[componentId]
    return {
        ...state,
        components: {
            ...components,
            [groupId]: {
                ...parentComponent,
                subComponents: {
                    ...subComponents,
                    [componentId]: {
                        ...customComponent,
                        ...data
                    }
                }
            }
        }
    }
}

function updateComponent(state, componentId, data) {
    const {components} = state
    const component = get(components, componentId)
    if (component) {
        return {
            ...state,
            components: {
                ...components,
                [componentId]: {
                    ...components[componentId],
                    ...data
                }
            }
        }
    } else {
        return state
    }
}

function updateComponentMinorVersion(state, componentId, minorVersion, data) {
    const {components} = state
    return updateComponent(state, componentId, {
        minorVersions: {
            ...components[componentId].minorVersions,
            [minorVersion]: {
                ...components[componentId].minorVersions[minorVersion],
                ...data
            }
        }
    })
}

function updateComponentVersion(state, componentId, minorVersion, version, data) {
    const {components} = state
    return updateComponentMinorVersion(state, componentId, minorVersion, {
        versions: {
            ...components[componentId].minorVersions[minorVersion].versions,
            [version]: {
                ...components[componentId].minorVersions[minorVersion].versions[version],
                ...data,
            }
        }
    })
}

export default componentsReducer
