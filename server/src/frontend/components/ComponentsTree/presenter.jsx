import React from 'react'
import {Spinner, Tree} from "@blueprintjs/core";
import Search from "./Search/Search.jsx";
import {getSecondaryLabel} from "../common";

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
    return Object.values(components).map(component => {
        let childNodes = []
        let componentId = component.id;
        if (component.minorVersions) {
            childNodes = renderComponentMinorVersions(componentId, component.minorVersions, props)
        }

        const isLoading = component && component.loadingMinorVersions
        const isError = component && component.loadingError
        const errorMessage = component.loadingErrorMessage

        return {
            id: componentId,
            level: treeLevel.ROOT,
            componentId: componentId,
            isExpanded: component.expand,
            label: component.name,
            icon: component.expand ? 'folder-open' : 'folder-close',
            childNodes: childNodes,
            secondaryLabel: getSecondaryLabel(isLoading, isError, errorMessage)
        }
    })
}

function renderComponentMinorVersions(componentId, minorVersions, props) {
    return Object.values(minorVersions).map(minorVersion => {
        let childNodes = []
        const minorVersionId = minorVersion.id
        if (minorVersion.versions) {
            let versions = minorVersion.versions
            childNodes = renderComponentVersions(componentId, minorVersionId, versions, props)
        }

        const isLoading = minorVersion && minorVersion.loadingVersions
        const isError = minorVersion && minorVersion.loadingError
        const errorMessage = minorVersion && minorVersion.errorMessage

        return {
            level: treeLevel.MINOR,
            id: minorVersionId,
            label: minorVersionId,
            version: minorVersionId,
            componentId: componentId,
            icon: minorVersion.expand ? 'folder-open' : 'folder-close',
            isExpanded: minorVersion.expand,
            childNodes: childNodes,
            secondaryLabel: getSecondaryLabel(isLoading, isError, errorMessage)
        }
    })
}

function renderComponentVersions(componentId, minorVersion, versions, props) {
    const {showRc, currentArtifacts} = props
    const {selectedComponent, selectedVersion} = currentArtifacts
    return Object.values(versions).filter(version => {
        return showRc || version.status !== 'RC'
    }).map(version => {
        let versionId = version.version;
        const displayName = versionId + (version.status === 'RELEASE' ? '' : `-${version.status}`)
        return {
            id: versionId,
            label: displayName,
            version: versionId,
            minorVersion: minorVersion.id,
            componentId: componentId,
            icon: 'box',
            isSelected: selectedComponent === componentId && selectedVersion === versionId
        }
    })
}

export {
    componentsTree,
    treeLevel
}