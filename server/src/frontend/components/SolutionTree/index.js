import {Component} from 'react'
import './style.css'
import {connect} from "react-redux";
import {solutionTree, treeLevel} from "./presenter.jsx";
import {componentsOperations} from "../duck";
import queryString from "query-string";
import history from "../../utils/history";
import get from "lodash/get";

const mapStateToProps = (state) => {
    const {
        components, loadingComponents, loadingArtifactsList, currentArtifacts, errorMessage, confirmation, showRc
    } = state.components
    return {components, loadingComponents, loadingArtifactsList, currentArtifacts, errorMessage, confirmation, showRc}
}

const mapDispatchToProps = (dispatch) => {
    const fetchSolutions = () => {
        dispatch(componentsOperations.getComponents(true))
    }
    const expandComponent = (componentId) => {
        dispatch(componentsOperations.expandComponent(componentId))
    }
    const closeComponent = (componentId) => {
        dispatch(componentsOperations.closeComponent(componentId))
    }
    const fetchComponentMinorVersions = (componentId) => {
        dispatch(componentsOperations.getComponentMinorVersions(componentId))
    }
    const expandMinorVersion = (componentId, minorVersion) => {
        dispatch(componentsOperations.expandMinorVersion(componentId, minorVersion))
    }
    const closeMinorVersion = (componentId, minorVersion) => {
        dispatch(componentsOperations.closeMinorVersion(componentId, minorVersion))
    }
    const fetchComponentVersions = (componentId, minorVersion) => {
        dispatch(componentsOperations.getComponentVersions(componentId, minorVersion))
    }
    const expandVersion = (componentId, minorVersion, version) => {
        dispatch(componentsOperations.expandVersion(componentId, minorVersion, version))
    }

    const closeVersion = (componentId, minorVersion, version) => {
        dispatch(componentsOperations.closeVersion(componentId, minorVersion, version))
    }

    const fetchDependencies = (componentId, minorVersion, version) => {
        dispatch(componentsOperations.getDependencies(componentId, minorVersion, version))
    }

    const selectDependency = (solutionId, solutionMinor, solutionVersion, componentId, version) => {
        dispatch(componentsOperations.selectDependency(solutionId, solutionMinor, solutionVersion, componentId, version))
    }

    return {
        fetchSolutions,
        expandComponent,
        closeComponent,
        fetchComponentMinorVersions,
        expandMinorVersion,
        closeMinorVersion,
        fetchComponentVersions,
        expandVersion,
        closeVersion,
        fetchDependencies,
        selectDependency
    }
}

const mergeProps = (stateProps, dispatchProps, ownProps) => {
    return {
        ...stateProps,
        ...dispatchProps,
        ...ownProps
    }
}

const propsToUrl = (props) => {
    const currentUrlProps = queryString.parse(history.location.search)
    const {
        selectedSolutionId,
        selectedSolutionMinor,
        selectedSolutionVersion,
        selectedComponent,
        selectedVersion
    } = props.currentArtifacts
    return {
        ...currentUrlProps,
        solutionId: selectedSolutionId == null ? undefined : selectedSolutionId,
        solutionMinor: selectedSolutionMinor == null ? undefined : selectedSolutionMinor,
        solutionVersion: selectedSolutionVersion == null ? undefined : selectedSolutionVersion,
        dependencyId: selectedComponent == null ? undefined : selectedComponent,
        dependencyVersion: selectedVersion == null ? undefined : selectedVersion
    }
}

class SolutionsTree extends Component {
    componentDidUpdate(prev) {
        const urlState = propsToUrl(this.props)
        history.push({search: queryString.stringify(urlState)})
    }

    componentDidMount() {
        const urlProps = queryString.parse(history.location.search)
        const {solutionId, solutionMinor, solutionVersion, dependencyId, dependencyVersion} = urlProps
        const {
            fetchSolutions,
            fetchComponentMinorVersions,
            fetchComponentVersions,
            fetchDependencies,
            selectDependency
        } = this.props

        fetchSolutions()
        if (solutionId) {
            fetchComponentMinorVersions(solutionId)
            if (solutionMinor) {
                fetchComponentVersions(solutionId, solutionMinor)
                if (solutionVersion) {
                    fetchDependencies(solutionId, solutionMinor, solutionVersion)
                    if (dependencyId && dependencyVersion) {
                        selectDependency(solutionId, solutionMinor, solutionVersion, dependencyId, dependencyVersion)
                    }
                }
            }
        }
    }

    render() {
        return solutionTree({...this.props, handleNodeClick: this.handleNodeClick})
    }

    handleNodeClick = (nodeData, _nodePath, e) => {
        const {level} = nodeData
        switch (level) {
            case treeLevel.ROOT:
                this.handleClickOnRoot(nodeData)
                break
            case treeLevel.MINOR:
                this.handleMinorVersionSelect(nodeData)
                break
            case treeLevel.VERSION:
                this.handleVersionSelect(nodeData)
                break
            default:
                this.handleDependencySelect(nodeData)
        }
    }

    handleClickOnRoot = (nodeData) => {
        const {components, fetchComponentMinorVersions, expandComponent, closeComponent} = this.props
        const {componentId, isExpanded} = nodeData

        if (isExpanded) {
            closeComponent(componentId)
            return
        }

        const component = components[componentId]
        if (!component.minorVersions || component.loadError) {
            fetchComponentMinorVersions(componentId)
        } else {
            expandComponent(componentId)
        }
    }

    handleMinorVersionSelect = (nodeData) => {
        const {components, fetchComponentVersions, expandMinorVersion, closeMinorVersion} = this.props
        const {componentId, version, isExpanded} = nodeData

        if (isExpanded) {
            closeMinorVersion(componentId, version)
            return
        }
        const minorVersion = get(components, [componentId, 'minorVersions', version]);
        if (!minorVersion.versions || minorVersion.loadError) {
            fetchComponentVersions(componentId, version)
        } else {
            expandMinorVersion(componentId, version)
        }
    }

    handleVersionSelect = (nodeData) => {
        const {components, fetchDependencies, expandVersion, closeVersion} = this.props
        const {solutionId, solutionMinor, solutionVersion, isExpanded} = nodeData

        if (isExpanded) {
            closeVersion(solutionId, solutionMinor, solutionVersion)
            return
        }
        const version = get(components, [solutionId, 'minorVersions', solutionMinor, 'versions', solutionVersion]);
        if (!version.dependencies || version.loadError) {
            fetchDependencies(solutionId, solutionMinor, solutionVersion)
        } else {
            expandVersion(solutionId, solutionMinor, solutionVersion)
        }
    }

    handleDependencySelect = (nodeData) => {
        const {
            selectDependency
        } = this.props
        const {solutionId, solutionMinor, solutionVersion, componentId, version, isSelected} = nodeData
        if (!isSelected) {
            selectDependency(solutionId, solutionMinor, solutionVersion, componentId, version)
        }
    }
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps)(SolutionsTree)
