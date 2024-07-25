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
    const {loadingComponents, handleNodeClick} = props

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

    return Object.values(components).map(solution => {
        let childNodes = []
        let componentId = solution.id;
        let minorVersions = solution.minorVersions;
        if (minorVersions) {
            childNodes = renderMinors(componentId, minorVersions, props)
        }
        return {
            id: componentId,
            level: treeLevel.ROOT,
            componentId: componentId,
            isExpanded: solution.expand,
            label: solution.name,
            icon: solution.expand ? 'folder-open' : 'folder-close',
            childNodes: childNodes,
            secondaryLabel: getSecondaryLabel(solution)
        }
    })
}

function renderMinors(solutionId, minorVersions, props) {
    return Object.values(minorVersions).map(minorVersion => {
        let childNodes = []
        let minorVersionId = minorVersion.id;
        if (minorVersion.versions) {
            let versions = minorVersion.versions
            childNodes = renderVersions(solutionId, minorVersionId, versions, props)
        }

        return {
            level: treeLevel.MINOR,
            id: minorVersionId,
            label: minorVersionId,
            version: minorVersionId,
            componentId: solutionId,
            icon: minorVersion.expand ? 'folder-open' : 'folder-close',
            isExpanded: minorVersion.expand,
            childNodes: childNodes,
            secondaryLabel: getSecondaryLabel(minorVersion)
        }
    })
}

function renderVersions(solutionId, solutionMinor, solutionVersions, props) {
    const {showRc} = props
    return Object.values(solutionVersions)
        .filter(version => {
            return showRc || version.status !== 'RC'
        })
        .map(version => {
            let childNodes = []
            if (version.dependencies) {
                let dependencies = version.dependencies
                childNodes = renderDependencies(solutionId, solutionMinor, version.version, dependencies, props)
            }

            const displayName = version.version + (version.status === 'RELEASE' ? '' : `-${version.status}`)
            return {
                level: treeLevel.VERSION,
                id: version.id,
                label: displayName,
                solutionId: solutionId,
                solutionMinor: solutionMinor,
                solutionVersion: version.version,
                icon: 'box',
                isExpanded: version.expand,
                childNodes: childNodes,
                secondaryLabel: getSecondaryLabel(version)
            }
        })
}

function renderDependencies(solutionId, solutionMinor, solutionVersion, dependencies, props) {
    const {currentArtifacts} = props
    const {selectedSolutionId, selectedSolutionVersion, selectedComponent, selectedVersion} = currentArtifacts
    return Object.values(dependencies).map(dependency => {
        let dependencyId = dependency.component.id;
        let dependencyName = dependency.component.name;
        let version = dependency.version;
        let displayName = `${dependencyName}:${version}`;
        return {
            id: displayName,
            label: displayName,
            solutionId: solutionId,
            solutionVersion: solutionVersion,
            solutionMinor: solutionMinor,
            componentId: dependencyId,
            version: version,
            icon: 'box',
            isSelected: selectedComponent === dependencyId
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