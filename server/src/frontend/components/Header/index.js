import {Component} from 'react'
import header from "./presenter.jsx";
import {connect} from "react-redux";
import get from "lodash/get";

const mapStateToProps = (state) => {
    let username = get(state, "components.loggedUser.username")
    return {
        username
    }
}

const mapDispatchToProps = (dispatch) => {
    const logOut = () => {
        window.location.href = '/?redirect_url=' + encodeURIComponent(window.location.href)
    }
    return {
        logOut
    }
}

const mergeProps = (stateProps, dispatchProps, ownProps) => {
    return {
        ...stateProps,
        ...dispatchProps,
        ...ownProps
    }
}

class Header extends Component {
    render() {
        return header(this.props)
    }
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps)(Header)