import React from 'react'
import {Spinner, Tree} from "@blueprintjs/core";
import {getSecondaryLabel} from "../common";

const treeLevel = {
    ROOT: 'ROOT',
    MINOR: 'MINOR'
}

function componentsTree(props) {
    const {
        loadingComponents, handleNodeClick
    } = props

    const nodes = componentsToNodes(props)

    if (loadingComponents) {
        return <div className="load-components-wrapper">
            <Spinner size={50} intent="primary"/>
        </div>
    } else {
        return <div className="components-tree">
            <div className="components-tree-wrapper">
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
        const componentId = component.id
        if (component.minorVersions) {
            childNodes = renderComponentMinorVersions(componentId, component.minorVersions, props)
        }

        return {
            id: componentId,
            level: treeLevel.ROOT,
            componentId: componentId,
            isExpanded: component.expand,
            label: component.name,
            icon: component.solution ? 'applications' : 'application',
            childNodes: childNodes,
            secondaryLabel: getSecondaryLabel(component)
        }
    })
}

function renderComponentMinorVersions(componentId, minorVersions, props) {
    return Object.values(minorVersions).map(minorVersion => {
        let childNodes = []
        const minorVersionId = minorVersion.id
        const versions = minorVersion.versions
        if (versions) {
            childNodes = renderComponentVersions(componentId, minorVersionId, versions, props)
        }
        return {
            level: treeLevel.MINOR,
            id: minorVersionId,
            label: minorVersionId,
            version: minorVersionId,
            componentId: componentId,
            icon: 'filter',
            isExpanded: minorVersion.expand,
            childNodes: childNodes,
            secondaryLabel: getSecondaryLabel(minorVersion)
        }
    })
}

function renderComponentVersions(componentId, minorVersionId, versions, props) {
    const {showRc, currentArtifacts} = props
    const {selectedComponent, selectedVersion} = currentArtifacts
    return Object.values(versions).filter(version => {
        return showRc || version.status !== 'RC'
    }).map(version => {
        const versionId = version.version
        const displayName = versionId + (version.status === 'RELEASE' ? '' : `-${version.status}`)
        return {
            id: versionId,
            label: displayName,
            version: versionId,
            minorVersion: minorVersionId,
            componentId: componentId,
            icon: 'build',
            isSelected: selectedComponent === componentId && selectedVersion === versionId
        }
    })
}

export {
    componentsTree,
    treeLevel
}