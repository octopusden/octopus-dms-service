import React from 'react'
import {Spinner, Tree} from "@blueprintjs/core";
import {getSecondaryLabel} from "../common";

const treeLevel = {
    GROUP: 'GROUP',
    COMPONENT: 'COMPONENT',
    MINOR: 'MINOR',
    VERSION: 'VERSION',
    COMPONENT_VERSION: 'COMPONENT_VERSION',
}

function groupedComponentsTree(props) {
    const {loadingComponents, handleNodeClick} = props

    const nodes = groupsToNodes(props)

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

function groupsToNodes(props) {
    const {components} = props
    return Object.values(components).map(group => {
        let childNodes = []
        let groupId = group.id;
        let subComponents = group.subComponents;
        if (subComponents) {
            childNodes = renderComponents(groupId, subComponents, props)
        }
        return {
            id: groupId,
            level: treeLevel.GROUP,
            groupId: groupId,
            isExpanded: group.expand,
            label: group.name,
            icon: group.expand ? 'folder-open' : 'folder-close',
            childNodes: childNodes,
        }
    })
}

function renderComponents(groupId, subComponents, props) {
    return Object.values(subComponents).map(component => {
        let childNodes = []
        let componentId = component.id;
        let minorVersions = component.minorVersions;
        if (minorVersions) {
            childNodes = renderComponentMinorVersions(groupId, componentId, minorVersions, props)
        }

        return {
            level: treeLevel.COMPONENT,
            id: componentId,
            label: component.name,
            groupId: groupId,
            componentId: componentId,
            icon: component.expand ? 'folder-open' : 'folder-close',
            isExpanded: component.expand,
            childNodes: childNodes,
            secondaryLabel: getSecondaryLabel(component)
        }
    })
}

function renderComponentMinorVersions(groupId, componentId, minorVersions, props) {
    return Object.values(minorVersions).map(minorVersion => {
        let childNodes = []
        const minorVersionId = minorVersion.id
        if (minorVersion.versions) {
            let versions = minorVersion.versions
            childNodes = renderVersions(groupId, componentId, minorVersionId, versions, props)
        }
        return {
            level: treeLevel.MINOR,
            id: minorVersionId,
            label: minorVersionId,
            groupId: groupId,
            minor: minorVersionId,
            componentId: componentId,
            icon: minorVersion.expand ? 'folder-open' : 'folder-close',
            isExpanded: minorVersion.expand,
            childNodes: childNodes,
            secondaryLabel: getSecondaryLabel(minorVersion)
        }
    })
}

function renderVersions(groupId, componentId, minorVersion, versions, props) {
    const {showRc, currentArtifacts} = props
    const {selectedGroup, selectedComponent, selectedVersion} = currentArtifacts
    return Object.values(versions)
        .filter(solutionVersion => {
            return showRc || solutionVersion.status !== 'RC'
        })
        .map(componentVersion => {
            const displayName = componentVersion.version + (componentVersion.status === 'RELEASE' ? '' : `-${componentVersion.status}`)
            return {
                level: treeLevel.VERSION,
                id: componentVersion.id,
                label: displayName,
                groupId: groupId,
                componentId: componentId,
                minorVersion: minorVersion,
                version: componentVersion.version,
                icon: 'box',
                isSelected: selectedGroup === groupId && selectedComponent === componentId
                    && selectedVersion === componentVersion.version
            }
        })
}

export {
    groupedComponentsTree,
    treeLevel
}