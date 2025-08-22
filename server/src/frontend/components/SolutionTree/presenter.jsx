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
    const {loadingComponents, handleNodeClick, handleNodeExpand, handleNodeCollapse} = props

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
                    onNodeExpand={handleNodeExpand}
                    onNodeCollapse={handleNodeCollapse}
                />
            </div>
        </div>
    }
}

function solutionsToNodes(props) {
    const {components} = props

    return Object.values(components)
        .filter(component => {
            return props.searchQuery
                ?
                (component.id.toLowerCase().includes(props.searchQuery.toLowerCase())
                    ||
                    component.name.toLowerCase().includes(props.searchQuery.toLowerCase()))
                : true
        })
        .map(solution => {
            let childNodes = []
            const componentId = solution.id
            const minorVersions = solution.minorVersions
            if (minorVersions) {
                childNodes = renderMinors(componentId, minorVersions, props)
            }
            return {
                id: componentId,
                level: treeLevel.ROOT,
                componentId: componentId,
                isExpanded: solution.expand,
                label: solution.name,
                icon: 'applications',
                childNodes: childNodes,
                secondaryLabel: getSecondaryLabel(solution)
            }
        })
}

function renderMinors(solutionId, minorVersions, props) {
    return Object.values(minorVersions).map(minorVersion => {
        let childNodes = []
        const minorVersionId = minorVersion.id
        const versions = minorVersion.versions
        if (versions) {
            childNodes = renderVersions(solutionId, minorVersionId, versions, props)
        }

        return {
            level: treeLevel.MINOR,
            id: minorVersionId,
            label: minorVersionId,
            version: minorVersionId,
            componentId: solutionId,
            icon: 'filter',
            isExpanded: minorVersion.expand,
            childNodes: childNodes,
            secondaryLabel: getSecondaryLabel(minorVersion)
        }
    })
}

function renderVersions(solutionId, solutionMinor, solutionVersions, props) {
    const {currentArtifacts, showRc} = props
    const {selectedComponent, selectedVersion} = currentArtifacts
    return Object.values(solutionVersions)
        .filter(version => {
            return showRc || version.status !== 'RC'
        })
        .map(version => {
            let childNodes = []
            const dependencies = version.dependencies
            let solutionVersion = version.version;
            if (dependencies) {
                childNodes = renderDependencies(solutionId, solutionMinor, solutionVersion, dependencies, props)
            }

            const displayName = solutionVersion + (version.status === 'RELEASE' ? '' : `-${version.status}`)
            return {
                level: treeLevel.VERSION,
                id: version.id,
                label: displayName,
                solutionId: solutionId,
                solutionMinor: solutionMinor,
                solutionVersion: solutionVersion,
                icon: 'build',
                isExpanded: version.expand,
                isSelected: selectedComponent === solutionId && selectedVersion === solutionVersion,
                childNodes: childNodes,
                secondaryLabel: getSecondaryLabel(version)
            }
        })
}

function renderDependencies(solutionId, solutionMinor, solutionVersion, dependencies, props) {
    const {currentArtifacts} = props
    const {selectedSolutionId, selectedSolutionVersion, selectedComponent, selectedVersion} = currentArtifacts
    return Object.values(dependencies).map(dependency => {
        const componentId = dependency.component
        const version = dependency.version
        const displayName = `${componentId}:${version}`
        return {
            id: displayName,
            label: displayName,
            solutionId: solutionId,
            solutionVersion: solutionVersion,
            solutionMinor: solutionMinor,
            componentId: componentId,
            version: version,
            icon: 'inheritance',
            isSelected: selectedComponent === componentId
                && selectedVersion === version
                && selectedSolutionId === solutionId
                && selectedSolutionVersion === solutionVersion
        }
    })
}

export {
    solutionTree,
    treeLevel
}