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
        return <div className="grouped-tree">
            <div className="grouped-tree-wrapper">
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
    const {components, icon} = props
    return Object.values(components)
        .filter(component => {
            return props.searchQuery
                ?
                (component.id.toLowerCase().includes(props.searchQuery.toLowerCase())
                    ||
                    component.name.toLowerCase().includes(props.searchQuery.toLowerCase()))
                : true
        })
        .map(group => {
            let childNodes = []
            const groupId = group.id
            const subComponents = group.subComponents
            if (subComponents) {
                childNodes = renderComponents(groupId, subComponents, props)
            }
            return {
                id: groupId,
                level: treeLevel.GROUP,
                groupId: groupId,
                isExpanded: group.expand,
                label: group.name,
                icon: icon,
                childNodes: childNodes,
            }
        })
}

function renderComponents(groupId, subComponents, props) {
    return Object.values(subComponents).map(component => {
        let childNodes = []
        const componentId = component.id
        const minorVersions = component.minorVersions
        if (minorVersions) {
            childNodes = renderComponentMinorVersions(groupId, componentId, minorVersions, props)
        }

        return {
            level: treeLevel.COMPONENT,
            id: componentId,
            label: component.name,
            groupId: groupId,
            componentId: componentId,
            icon: component.solution ? 'applications' : 'application',
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
        const versions = minorVersion.versions
        if (versions) {
            childNodes = renderVersions(groupId, componentId, minorVersionId, versions, props)
        }
        return {
            level: treeLevel.MINOR,
            id: minorVersionId,
            label: minorVersionId,
            groupId: groupId,
            minor: minorVersionId,
            componentId: componentId,
            icon: 'filter',
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
        .filter(version => {
            return showRc || version.status !== 'RC'
        })
        .map(version => {
            const displayName = version.version + (version.status === 'RELEASE' ? '' : `-${version.status}`)
            return {
                level: treeLevel.VERSION,
                id: version.id,
                label: displayName,
                groupId: groupId,
                componentId: componentId,
                minorVersion: minorVersion,
                version: version.version,
                icon: 'build',
                isSelected: selectedGroup === groupId && selectedComponent === componentId
                    && selectedVersion === version.version
            }
        })
}

export {
    groupedComponentsTree,
    treeLevel
}