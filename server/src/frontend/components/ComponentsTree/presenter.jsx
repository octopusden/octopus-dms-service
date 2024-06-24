import React from 'react'
import {Icon, Spinner, Tooltip, Tree} from "@blueprintjs/core";
import Search from "./Search/Search.jsx";

const treeLevel = {
    ROOT: 'ROOT',
    MINOR: 'MINOR'
}

function componentsTree(props) {
    const {
        toggleRc, showRc, requestSearch, selectVersion, fetchComponentVersions, searchResult, searchQueryValid,
        searching, loadingComponents, handleNodeClick
    } = props

    const nodes = componentsToNodes(props)

    if (loadingComponents) {
        return <div className="load-components-wrapper">
            <Spinner size={50} intent="primary"/>
        </div>
    } else {
        return <div className="components-tree">
            <div className="components-tree-wrapper">
                <div className='search-block'>
                    <Search
                        toggleRc={toggleRc}
                        showRc={showRc}
                        requestSearch={requestSearch}
                        selectVersion={selectVersion}
                        fetchComponentVersions={fetchComponentVersions}
                        searchResult={searchResult}
                        searchQueryValid={searchQueryValid}
                        searching={searching}/>
                </div>
                <Tree
                    contents={nodes}
                    onNodeClick={handleNodeClick}
                    onNodeCollapse={handleNodeClick}
                    onNodeExpand={handleNodeClick}
                />
            </div>
        </div>
    }
}

function componentsToNodes(props) {
    const {components} = props

    return Object.entries(components).map(entry => {
        const [key, cur] = entry
        let childNodes = []
        let component = components[key]
        if (component.minorVersions) {
            let versions = component.minorVersions
            childNodes = renderComponentMinorVersions(versions, key, props)
        }

        const isLoading = component && component.loadingMinorVersions
        const isError = component && component.loadingError
        const errorMessage = component.loadingErrorMessage

        return {
            id: key,
            level: treeLevel.ROOT,
            componentId: key,
            isExpanded: cur.expand,
            label: cur.name,
            icon: cur.expand ? 'folder-open' : 'folder-close',
            childNodes: childNodes,
            secondaryLabel: getSecondaryLabel(isLoading, isError, errorMessage)
        }
    })
}

function renderComponentMinorVersions(versions, currentComponent, props) {
    const {components, showRc, currentArtifacts} = props
    const {selectedComponent, selectedVersion} = currentArtifacts

    return Object.entries(versions).map(entry => {
        const [versionId, v] = entry

        let childNodes = []
        let minorVersion = components[currentComponent].minorVersions[versionId]
        if (minorVersion.versions) {
            let versions = minorVersion.versions
            childNodes = renderComponentVersions(versions, currentComponent, versionId, showRc, selectedComponent, selectedVersion)
        }

        const isLoading = minorVersion && minorVersion.loadingVersions
        const isError = minorVersion && minorVersion.loadingError
        const errorMessage = minorVersion && minorVersion.errorMessage

        return {
            level: treeLevel.MINOR,
            id: versionId,
            label: versionId,
            version: versionId,
            componentId: currentComponent,
            icon: v.expand ? 'folder-open' : 'folder-close',
            isExpanded: v.expand,
            childNodes: childNodes,
            secondaryLabel: getSecondaryLabel(isLoading, isError, errorMessage)
        }
    })
}

function renderComponentVersions(versions, currentComponent, currentMinorVersion, showRc, selectedComponent, selectedVersion) {
    return versions.filter(e => {
        return showRc || e.status !== 'RC'
    }).map(v => {
        const displayName = v.version + (v.status === 'RELEASE' ? '' : `-${v.status}`)
        return {
            id: v.version,
            label: displayName,
            version: v.version,
            minorVersion: currentMinorVersion,
            componentId: currentComponent,
            icon: 'box',
            isSelected: selectedComponent === currentComponent && selectedVersion === v.version
        }
    })
}

function getSecondaryLabel(isLoading, isError, errorMessage) {
    let secondaryLabel
    if (isError) {
        secondaryLabel = <Tooltip
            content={errorMessage}
            position='top-right'
        >
            <div className='error-icon-components-tree-wrap'>
                <Icon icon='error' size={16} intent="danger"/>
            </div>
        </Tooltip>
    }
    if (isLoading) {
        secondaryLabel = <Spinner size={16} intent="primary"/>
    }
    return secondaryLabel
}


export {
    componentsTree,
    treeLevel
}