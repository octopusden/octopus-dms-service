import {Component} from 'react'
import './style.css'
import {connect} from "react-redux";
import {solutionTree, treeLevel} from "./presenter.jsx";
import {componentsOperations} from "../duck";
import queryString from "query-string";
import history from "../../utils/history";

const mapStateToProps = (state) => {
    const {
        loggedUser, components, componentsVersions, loadingComponents, loadingArtifactsList, currentArtifacts,
        errorMessage, showRc, searchQueryValid, searchResult, searching, toggleAdminMode, adminMode, confirmation
    } = state.components
    return {
        loggedUser,
        components,
        componentsVersions,
        loadingComponents,
        loadingArtifactsList,
        currentArtifacts,
        errorMessage,
        showRc,
        searchQueryValid,
        searchResult,
        searching,
        toggleAdminMode,
        adminMode,
        confirmation
    }
}

const mapDispatchToProps = (dispatch) => {
    const toggleRc = () => {
        dispatch(componentsOperations.toggleRc())
    }
    const fetchComponents = () => {
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

    const selectDependency = (componentId, minorVersion, version, dependency) => {
        dispatch(componentsOperations.selectDependency(componentId, minorVersion, version, dependency))
    }

    return {
        toggleRc,
        fetchComponents,
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
    const {selectedComponent, selectedVersion, selectedMinor} = props.currentArtifacts

    return {
        ...currentUrlProps,
        component: selectedComponent == null ? undefined : selectedComponent,
        version: selectedVersion == null ? undefined : selectedVersion,
        minor: selectedMinor == null ? undefined : selectedMinor,
    }
}

class ComponentsTree extends Component {

    componentDidUpdate(prev) {
        const urlState = propsToUrl(this.props)
        history.push({search: queryString.stringify(urlState)})
    }

    componentDidMount() {
        const urlProps = queryString.parse(history.location.search)
        console.debug('urlProps', urlProps)
        const {component, minor, version, dependency} = urlProps
        const {fetchComponents, fetchComponentMinorVersions, fetchComponentVersions, fetchDependencies, selectDependency} = this.props

        fetchComponents()
        if (component) {
            fetchComponentMinorVersions(component)
            if (minor) {
                fetchComponentVersions(component, minor)
                if (version) {
                    fetchDependencies(component, minor, version)
                    if (dependency) {
                        selectDependency(component, minor, version, dependency)
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

        if (!components[componentId].minorVersions || components[componentId].loadingError) {
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

        if (!components[componentId].minorVersions[version].versions || components[componentId].minorVersions.loadingError) {
            fetchComponentVersions(componentId, version)
        } else {
            expandMinorVersion(componentId, version)
        }
    }

    handleVersionSelect = (nodeData) => {
        const {components, fetchDependencies, expandVersion, closeVersion} = this.props
        const {componentId, minorVersion, version, isExpanded} = nodeData

        if (isExpanded) {
            closeVersion(componentId, minorVersion, version)
            return
        }
        if (!components[componentId].minorVersions[minorVersion].versions[version].dependencies || components[componentId].minorVersions[minorVersion].versions.loadingError) {
            fetchDependencies(componentId, minorVersion, version)
        } else {
            expandVersion(componentId, minorVersion, version)
        }
    }

    handleDependencySelect = (nodeData) => {
        const {
            selectDependency
        } = this.props

        const {componentId, minorVersion, version, dependency, isSelected} = nodeData
        if (!isSelected) {
            selectDependency(componentId, minorVersion, version, dependency)
        }
    }

}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps)(ComponentsTree)
