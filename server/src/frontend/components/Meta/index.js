import React, { Component } from 'react'
import meta from "./presenter.jsx";

export default class Meta extends Component {
  render () {
    return meta(this.props)
  }
}
