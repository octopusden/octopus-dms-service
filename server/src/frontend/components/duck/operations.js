import actions from './actions'
import {checkQuery} from '../common.js'

const handleErrors = (operation) => (response) => {
    if (!response.ok) {
        console.error(response)
        throw Error(`"${operation}" failed with error code ${response.status}:${response.statusText}`)
    }
    return response
}

const getBuildInfo = () => (dispatch) => {
    dispatch(actions.requestBuildInfo())
    fetch('actuator/info')
        .then(handleErrors('Get Build Info'))
        .then((response) => {
            response.json().then((data) => {
                dispatch(actions.receiveBuildInfo(data))
            })
        }).catch((err) => dispatch(actions.showError(err.message)))
}

const getLoggedUser = () => (dispatch) => {
    dispatch(actions.requestLoggedUser())
    fetch('auth/me')
        .then(handleErrors('Get logged user'))
        .then((response) => {
            response.json().then((data) => {
                dispatch(actions.receiveLoggedUser(data))
            })
        }).catch((err) => dispatch(actions.showError(err.message)))
}

const getComponents = (solution, onSuccess) => (dispatch) => {
    dispatch(actions.requestComponents())
    fetch(`rest/api/3/components?solution=${solution}`)
        .then(handleErrors('Get components'))
        .then((response) => {
            response.json().then((data) => {
                const components = data.components.reduce((map, c) => {
                    map[c.id] = c
                    return map
                }, {});
                dispatch(actions.receiveComponents(components))
                if (onSuccess) {
                    onSuccess()
                }
            })
        }).catch((err) => dispatch(actions.showError(err.message)))
}

const getClientComponents = (onSuccess) => (dispatch) => {
    dispatch(actions.requestComponents())
    fetch(`rest/api/3/components`)
        .then(handleErrors('Get client components'))
        .then((response) => {
            response.json().then((data) => {
                const parents = data.components
                    .filter(c => c.clientCode)
                    .reduce(function (acc, component) {
                        const clientCode = component.clientCode
                        if (!acc[clientCode]) {
                            acc[clientCode] = {id: clientCode, name: clientCode}
                            acc[clientCode].subComponents = {}
                        }
                        acc[clientCode].subComponents[component.id] = component;
                        return acc;
                    }, {})
                dispatch(actions.receiveComponents(parents))
                if (onSuccess) {
                    onSuccess()
                }
            })
        }).catch((err) => dispatch(actions.showError(err.message)))
}

const getCustomComponents = (onSuccess) => (dispatch) => {
    dispatch(actions.requestComponents())
    fetch(`rest/api/3/components`)
        .then(handleErrors('Get custom components'))
        .then((response) => {
            response.json().then((data) => {
                fetch(`rest/api/3/components?explicit=false`)
                    .then(handleErrors('Get custom component parents'))
                    .then((responseWithImplicit) => {
                        responseWithImplicit.json().then((allData) => {
                            const idComponents = allData.components.reduce((map, c) => {
                                map[c.id] = c
                                return map
                            }, {})
                            const parents = data.components
                                .filter((c) => c.parentComponent)
                                .reduce(function (acc, component) {
                                    const parentComponentId = component.parentComponent
                                    if (!acc[parentComponentId]) {
                                        acc[parentComponentId] = idComponents[parentComponentId] ? idComponents[parentComponentId] : parentComponentId
                                        acc[parentComponentId].subComponents = {}
                                    }
                                    acc[parentComponentId].subComponents[component.id] = component;
                                    return acc;
                                }, {})
                            dispatch(actions.receiveComponents(parents))
                            if (onSuccess) {
                                onSuccess()
                            }
                        })
                    }).catch((err) => dispatch(actions.showError(err.message)))
            })
        }).catch((err) => dispatch(actions.showError(err.message)))
}

const getComponentMinorVersions = (componentId, onSuccess) => (dispatch) => {
    dispatch(actions.requestComponentMinorVersions(componentId))
    fetch(`rest/api/3/components/${componentId}/minor-versions`).then((response) => {
        response.json().then((data) => {
            if (response.ok) {
                const versions = data.reduce((map, e) => {
                    map[e] = {id: e}
                    return map
                }, {})
                dispatch(actions.receiveComponentMinorVersions(componentId, versions))
                dispatch(actions.expandComponent(componentId))
                if (onSuccess) {
                    onSuccess()
                }
            } else {
                const {message} = data
                dispatch(actions.receiveComponentMinorVersionsError(componentId, message))
                dispatch(actions.showError(message))
            }
        })
    })
}

const getComponentVersions = (componentId, minorVersion, onSuccess) => (dispatch) => {
    dispatch(actions.requestComponentVersions(componentId, minorVersion))
    fetch(`rest/api/3/components/${componentId}/versions?filter-by-minor=${minorVersion}&includeRc=true`).then((response) => {
        response.json().then((data) => {
            if (response.ok) {
                const versions = data.versions.reduce((map, e) => {
                    map[e.version] = e
                    return map
                }, {})
                dispatch(actions.receiveComponentVersions(componentId, minorVersion, versions))
                dispatch(actions.expandMinorVersion(componentId, minorVersion))
                if (onSuccess) {
                    onSuccess()
                }
            } else {
                const {message} = data
                dispatch(actions.receiveComponentVersionsError(componentId, minorVersion, message))
                dispatch(actions.showError(message))
            }
        })
    })
}

const getDependencies = (componentId, minorVersion, version, onSuccess) => (dispatch) => {
    dispatch(actions.requestDependencies(componentId, minorVersion, version))
    fetch(`rest/api/3/components/${componentId}/versions/${version}/dependencies`).then((response) => {
        response.json().then((data) => {
            if (response.ok) {
                const dependencies = data.reduce((map, d) => {
                    map[`${d.component.id}:${d.version}`] = d
                    return map
                }, {})
                dispatch(actions.receiveDependencies(componentId, minorVersion, version, dependencies))
                dispatch(actions.expandVersion(componentId, minorVersion, version))
                if (onSuccess) {
                    onSuccess()
                }
            } else {
                const {message} = data
                dispatch(actions.receiveDependenciesError(componentId, minorVersion, version, message))
                dispatch(actions.showError(message))
            }
        })
    })
}

const expandComponent = (componentId) => (dispatch) => {
    dispatch(actions.expandComponent(componentId))
}

const expandVersion = (componentId, minorVersion, version) => (dispatch) => {
    dispatch(actions.expandVersion(componentId, minorVersion, version))
}

const closeVersion = (componentId, minorVersion, version) => (dispatch) => {
    dispatch(actions.closeVersion(componentId, minorVersion, version))
}

const selectDependency = (solutionId, solutionMinor, solutionVersion, componentId, version) => (dispatch) => {
    dispatch(actions.selectDependency(solutionId, solutionMinor, solutionVersion, componentId, version))
}

const closeComponent = (componentId) => (dispatch) => {
    dispatch(actions.closeComponent(componentId))
}

const expandGroupedComponent = (groupId, componentId) => (dispatch) => {
    dispatch(actions.expandGroupedComponent(groupId, componentId))
}

const closeGroupedComponent = (groupId, componentId) => (dispatch) => {
    dispatch(actions.closeGroupedComponent(groupId, componentId))
}

const getGroupedComponentMinorVersions = (groupId, componentId, onSuccess) => (dispatch) => {
    dispatch(actions.requestGroupedComponentMinorVersions(groupId, componentId))
    fetch(`rest/api/3/components/${componentId}/minor-versions`).then((response) => {
        response.json().then((data) => {
            if (response.ok) {
                const versions = data.reduce((map, e) => {
                    map[e] = {id: e}
                    return map
                }, {});
                dispatch(actions.receiveGroupedComponentMinorVersions(groupId, componentId, versions))
                dispatch(actions.expandGroupedComponent(groupId, componentId))
                if (onSuccess) {
                    onSuccess()
                }
            } else {
                const {message} = data
                dispatch(actions.receiveGroupedComponentMinorVersionsError(groupId, componentId, message))
                dispatch(actions.showError(message))
            }
        })
    })
}

const expandGroupedComponentMinorVersion = (groupId, componentId, minorVersion) => (dispatch) => {
    dispatch(actions.expandGroupedComponentMinorVersion(groupId, componentId, minorVersion))
}

const closeGroupedComponentMinorVersion = (groupId, componentId, minorVersion) => (dispatch) => {
    dispatch(actions.closeGroupedComponentMinorVersion(groupId, componentId, minorVersion))
}

const getGroupedComponentVersions = (groupId, componentId, minorVersion, onSuccess) => (dispatch) => {
    dispatch(actions.requestGroupedComponentVersions(groupId, componentId, minorVersion))
    fetch(`rest/api/3/components/${componentId}/versions?filter-by-minor=${minorVersion}&includeRc=true`).then((response) => {
        response.json().then((data) => {
            if (response.ok) {
                const versions = data.versions.reduce((map, e) => {
                    map[e.version] = e
                    return map
                }, {})
                dispatch(actions.receiveGroupedComponentVersions(groupId, componentId, minorVersion, versions))
                dispatch(actions.expandGroupedComponentMinorVersion(groupId, componentId, minorVersion))
                if (onSuccess) {
                    onSuccess()
                }
            } else {
                const {message} = data
                dispatch(actions.receiveGroupedComponentVersionsError(groupId, componentId, minorVersion, message))
                dispatch(actions.showError(message))
            }
        })
    })
}

const selectGroupedComponentVersion = (groupId, componentId, minorVersion, version) => (dispatch) => {
    dispatch(actions.selectGroupedComponentVersion(groupId, componentId, minorVersion, version))
}

const expandMinorVersion = (componentId, minorVersion) => (dispatch) => {
    dispatch(actions.expandMinorVersion(componentId, minorVersion))
}

const closeMinorVersion = (componentId, minorVersion) => (dispatch) => {
    dispatch(actions.closeMinorVersion(componentId, minorVersion))
}

const selectVersion = (componentId, minorVersion, version) => (dispatch) => {
    dispatch(actions.selectVersion(componentId, minorVersion, version))
}

const getArtifactsList = (componentId, version) => (dispatch) => {
    dispatch(actions.requestArtifactsList(componentId, version))
    fetch(`rest/api/3/components/${componentId}/versions/${version}/artifacts`).then((response) => {
        response.json().then((data) => {
            if (response.ok) {
                dispatch(actions.receiveArtifactsList(data.artifacts))
            } else {
                dispatch(actions.showError(data.message))
            }
        })
    })
}

const getDocument = (componentId, version, id, displayName) => (dispatch) => {
    dispatch(actions.requestDocumentArtifact(id))
    fetch(`rest/api/3/components/${componentId}/versions/${version}/artifacts/${id}/download`).then((response) => {
        response.text().then((data) => {
            dispatch(actions.receiveDocumentArtifact(id, displayName, true, data))
        })
    })
}

const deleteArtifact = (componentId, version, id) => (dispatch) => {
    dispatch(actions.deleteArtifact(id))
    const options = {
        method: 'DELETE'
    }
    fetch(`rest/api/3/components/${componentId}/versions/${version}/artifacts/${id}?dry-run=false`, options)
        .then(handleErrors(`Delete artifact '${id}'`))
        .then((_) => {
            dispatch(actions.successDeleteArtifact(id))
            dispatch(getArtifactsList(componentId, version))
        })
        .catch((err) => dispatch(actions.showError(err.message)))
}

const getEmptyDocumentArtifact = (componentId, version, id) => (dispatch) => {
    dispatch(actions.receiveDocumentArtifact(id, undefined, false, undefined))
}

const showError = (errorMessage) => (dispatch) => {
    dispatch(actions.showError(errorMessage))
}

const hideError = () => (dispatch) => {
    dispatch(actions.hideError())
}

const toggleRc = () => (dispatch) => {
    dispatch(actions.toggleRc())
}

const toggleAdminMode = () => (dispatch) => {
    dispatch(actions.toggleAdminMode())
}

const handleComponentGroupTabChange = (selectedComponentGroupTab) => (dispatch) => {
    dispatch(actions.handleComponentGroupTabChange(selectedComponentGroupTab))
}

const showConfirmation = (message, onConfirm) => (dispatch) => {
    dispatch(actions.showConfirmation(message, onConfirm))
}

const hideConfirmation = () => (dispatch) => {
    dispatch(actions.hideConfirmation())
}

var requestSearchTimer
const requestSearch = (query) => (dispatch) => {
    clearTimeout(requestSearchTimer)
    const isQueryValid = checkQuery(query)
    dispatch(actions.changeSearchQueryValid(isQueryValid))

    if (isQueryValid) {
        requestSearchTimer = setTimeout(() => {
            dispatch(actions.requestSearch(query))

            var url = new URL(`${window.location.protocol}//${window.location.host}/ui/search`)
            var params = {query: query}
            Object.keys(params).forEach(key => url.searchParams.append(key, params[key]))
            fetch(url)
                .then(handleErrors('Search by query'))
                .then((response) => {
                    response.json().then((data) => {
                        dispatch(actions.receiveSearch(data))
                    })
                })
                .catch((err) => dispatch(actions.showError(err.message)))

            console.log(query)
        }, 500)
    }
}

export default {
    getBuildInfo,
    getLoggedUser,
    getComponents,
    getClientComponents,
    getCustomComponents,
    getComponentVersions,
    getDependencies,
    expandComponent,
    expandMinorVersion,
    expandVersion,
    closeVersion,
    selectDependency,
    closeComponent,
    getArtifactsList,
    getDocument,
    getEmptyDocumentArtifact,
    showError,
    hideError,
    toggleRc,
    toggleAdminMode,
    handleComponentGroupTabChange,
    requestSearch,
    getComponentMinorVersions,
    closeMinorVersion,
    selectVersion,
    expandGroupedComponent,
    closeGroupedComponent,
    getGroupedComponentMinorVersions,
    expandGroupedComponentMinorVersion,
    closeGroupedComponentMinorVersion,
    getGroupedComponentVersions,
    selectGroupedComponentVersion,
    deleteArtifact,
    showConfirmation,
    hideConfirmation
}
