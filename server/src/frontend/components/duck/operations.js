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

const getComponents = () => (dispatch) => {
    dispatch(actions.requestComponents())
    fetch('rest/api/3/components')
        .then(handleErrors('Get components'))
        .then((response) => {
            response.json().then((data) => {
                const components = data.components.reduce((map, c) => {
                    map[c.id] = c
                    return map
                }, {});
                dispatch(actions.receiveComponents(components))
            })
        }).catch((err) => dispatch(actions.showError(err.message)))
}

const getComponentMinorVersions = (componentId) => (dispatch) => {
    dispatch(actions.requestComponentMinorVersions(componentId))
    fetch(`rest/api/3/components/${componentId}/minor-versions`).then((response) => {
        response.json().then((data) => {
            if (response.ok) {
                let versions = data.reduce((map, e) => {
                    map[e] = {}
                    return map
                }, {})
                console.debug("minorVersions", versions)
                dispatch(actions.receiveComponentMinorVersions(componentId, versions))
                dispatch(actions.expandComponent(componentId))
            } else {
                let {message} = data
                dispatch(actions.receiveComponentMinorVersionsError(componentId, message))
                dispatch(actions.showError(message))
            }
        })
    })
}

const getComponentVersions = (componentId, minorVersion) => (dispatch) => {
    dispatch(actions.requestComponentVersions(componentId, minorVersion))
    fetch(`rest/api/3/components/${componentId}/versions?filter-by-minor=${minorVersion}&includeRc=true`).then((response) => {
        response.json().then((data) => {
            if (response.ok) {
                let versions = data.versions
                dispatch(actions.receiveComponentVersions(componentId, minorVersion, versions))
                dispatch(actions.expandMinorVersion(componentId, minorVersion))
            } else {
                let {message} = data
                dispatch(actions.receiveComponentVersionsError(componentId, minorVersion, message))
                dispatch(actions.showError(message))
            }
        })
    })
}

const expandComponent = (componentId) => (dispatch) => {
    dispatch(actions.expandComponent(componentId))
}

const closeComponent = (componentId) => (dispatch) => {
    dispatch(actions.closeComponent(componentId))
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

const getArtifactsList = (componentId, minorVersion, version) => (dispatch) => {
    dispatch(actions.requestArtifactsList(componentId, minorVersion, version))
    fetch(`rest/api/3/components/${componentId}/versions/${version}/artifacts`).then((response) => {
        response.json().then((data) => {
            if (response.ok) {
                let artifacts = data.artifacts
                dispatch(actions.receiveArtifactsList(componentId, minorVersion, version, artifacts))
            } else {
                dispatch(actions.showError(data.message))
            }
        })
    })
}

const getDocumentArtifact = (componentId, version, id, displayName) => (dispatch) => {
    dispatch(actions.requestDocumentArtifact(id))
    fetch(`rest/api/3/components/${componentId}/versions/${version}/artifacts/${id}/download`).then((response) => {
        response.text().then((data) => {
            dispatch(actions.receiveDocumentArtifact(id, displayName, true, data))
        })
    })
}

const deleteArtifact = (componentId, minorVersion, version, id) => (dispatch) => {
    dispatch(actions.deleteArtifact(id))
    const options = {
        method: 'DELETE'
    }
    fetch(`rest/api/3/artifacts/${id}?dry-run=false`, options)
        .then(handleErrors(`Delete artifact '${id}'`))
        .then((_) => {
            dispatch(actions.successDeleteArtifact(id))
            dispatch(getArtifactsList(componentId, minorVersion, version))
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

const showConfirmation = (message, onConfirm) => (dispatch) => {
    dispatch(actions.showConfirmation(message, onConfirm))
}

const hideConfirmation = () => (dispatch) => {
    dispatch(actions.hideConfirmation())
}

var requestSearchTimer
const requestSearch = (query) => (dispatch) => {
    clearTimeout(requestSearchTimer)
    let isQueryValid = checkQuery(query)
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
    getComponentVersions,
    expandComponent,
    closeComponent,
    getArtifactsList,
    getDocumentArtifact,
    getEmptyDocumentArtifact,
    showError,
    hideError,
    toggleRc,
    toggleAdminMode,
    requestSearch,
    getComponentMinorVersions,
    expandMinorVersion,
    closeMinorVersion,
    selectVersion,
    deleteArtifact,
    showConfirmation,
    hideConfirmation
}
