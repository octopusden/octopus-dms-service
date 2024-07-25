import React, {Component} from 'react'
import {connect} from 'react-redux'
import ComponentsComponent from './Components'
import {componentsOperations} from './duck'
import get from "lodash/get";

const mapStateToProps = (state) => {
    const {buildInfo, loggedUser} = get(state, "components")
    return {
        buildInfo, loggedUser
    }
}

const mapDispatchToProps = (dispatch) => {
    const getBuildInfo = () => {
        dispatch(componentsOperations.getBuildInfo())
    }
    const getLoggedUser = () => {
        dispatch(componentsOperations.getLoggedUser())
    }
    const showError = (errorMessage) => {
        dispatch(componentsOperations.showError(errorMessage))
    }
    const hideError = () => {
        dispatch(componentsOperations.hideError())
    }
    const showConfirmation = (message, onConfirm) => {
        dispatch(componentsOperations.showConfirmation(message, onConfirm))
    }
    const hideConfirmation = () => {
        dispatch(componentsOperations.hideConfirmation())
    }
    return {
        getBuildInfo,
        getLoggedUser,
        showError,
        hideError,
        showConfirmation,
        hideConfirmation
    }
}

class App extends Component {

    componentDidMount() {
        const {getBuildInfo, getLoggedUser} = this.props
        getBuildInfo()
        getLoggedUser()
    }

    render() {
        return <ComponentsComponent {...this.props}/>
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(App)
