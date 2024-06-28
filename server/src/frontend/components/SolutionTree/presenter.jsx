import React from 'react'
import {Spinner, Tree} from "@blueprintjs/core";
import {getSecondaryLabel} from "../common";

const treeLevel = {
    ROOT: 'ROOT',
    MINOR: 'MINOR',
    VERSION: 'VERSION',
    COMPONENT_VERSION: 'COMPONENT_VERSION',
}

function solutionTree(props) {
    const {
        toggleRc, showRc, requestSearch, selectVersion, fetchComponentVersions, searchResult, searchQueryValid,
        searching, loadingComponents, handleNodeClick
    } = props

    const nodes = solutionsToNodes(props)

    if (loadingComponents) {
        return <div className="load-components-wrapper">
            <Spinner size={50} intent="primary"/>
        </div>
    } else {
        return <div className="solution-tree">
            <div className="solution-tree-wrapper">
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

function solutionsToNodes(props) {
    const {components} = props

    return Object.entries(components).map(entry => {
        const [key, cur] = entry
        let childNodes = []
        let component = components[key]
        if (component.minorVersions) {
            let versions = component.minorVersions
            childNodes = renderMinors(versions, key, props)
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

function renderMinors(versions, currentComponent, props) {
    const {components, showRc, currentArtifacts} = props

    return Object.entries(versions).map(entry => {
        const [versionId, v] = entry

        let childNodes = []
        let minorVersion = components[currentComponent].minorVersions[versionId]
        if (minorVersion.versions) {
            let versions = minorVersion.versions
            childNodes = renderVersions(versions, currentComponent, versionId, props)
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

function renderVersions(versions, currentComponent, currentMinor, props) {
    const {components, showRc} = props

    return Object.entries(versions).filter(entry => {
        const [versionId, v] = entry
        return showRc || v.status !== 'RC'
    }).map(entry => {
        const [versionId, v] = entry

        let childNodes = []
        let version = components[currentComponent].minorVersions[currentMinor].versions[versionId]
        if (version.dependencies) {
            let dependencies = version.dependencies
            childNodes = renderDependencies(dependencies, currentComponent, currentMinor, versionId, props)
        }

        // const isLoading = version && version.loadingVersions
        // const isError = version && version.loadingError
        // const errorMessage = version && version.errorMessage

        const displayName = v.version + (v.status === 'RELEASE' ? '' : `-${v.status}`)
        return {
            level: treeLevel.VERSION,
            id: versionId,
            label: displayName,
            version: v.version,
            minorVersion: currentMinor,
            componentId: currentComponent,
            icon: 'box',
            isExpanded: v.expand,
            childNodes: childNodes,
            // secondaryLabel: getSecondaryLabel(isLoading, isError, errorMessage)
        }
    })
}

function renderDependencies(dependencies, currentComponent, currentMinor, currentVersion, props) {
    const {currentArtifacts} = props
    const {selectedComponent, selectedVersion, selectedDependency } = currentArtifacts
    console.log("selectedComponent currentComponent", selectedComponent, currentComponent)
    console.log("selectedVersion currentVersion", selectedVersion, currentVersion)


    return dependencies.map(d => {
        let displayName = `${d.component.name}:${d.version}`;
        console.log("selectedDependency displayName", selectedDependency, displayName)
        return {
            id: displayName,
            label: displayName,
            version: currentVersion,
            minorVersion: currentMinor,
            componentId: currentComponent,
            icon: 'box',
            isSelected: selectedComponent === currentComponent && selectedVersion === currentVersion && selectedDependency === displayName
        }
    })
}

export {
    solutionTree,
    treeLevel
}